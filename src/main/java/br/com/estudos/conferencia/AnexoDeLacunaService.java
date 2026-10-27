package br.com.estudos.conferencia;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.estudos.edital.EditalArquivos;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * O PDF que a pessoa anexa para preencher uma lacuna do material (01_DOMINIO §3.14, D-97; ADR-049; docs/SPRINT-28). O SGE guarda
 * o arquivo no volume {@code editais}; quem o corta em segmentos e os importa é o worker do planejamento, que devolve o resultado.
 */
@Service
public class AnexoDeLacunaService {

    static final int MAXIMO_DE_PAGINAS = 3000;

    public record AnexoPendente(Long id, String chaveExterna, String assunto, String parte, String arquivo, int paginas, String nome, int proximaOrdem) {}

    private final JdbcTemplate jdbc;
    private final EditalArquivos arquivos;
    private final MaterialDaConferencia material;

    public AnexoDeLacunaService(JdbcTemplate jdbc, EditalArquivos arquivos, MaterialDaConferencia material) {
        this.jdbc = jdbc;
        this.arquivos = arquivos;
        this.material = material;
    }

    @Transactional
    public MaterialDaConferencia.Info anexar(Long assuntoId, String parte, MultipartFile pdf) {
        var info = material.porAssunto(List.of(assuntoId)).get(assuntoId);
        if (info == null || parte == null || info.lacunas().stream().noneMatch(l -> l.parte().equals(parte))) {
            throw new NotFoundException("Este assunto não tem essa lacuna.", "LACUNA_INEXISTENTE");
        }
        long editalAssuntoId = editalAssuntoId(assuntoId);
        var existente = jdbc.queryForList("SELECT id, estado, arquivo FROM lacuna_anexo WHERE assunto_edital_id = ? AND parte = ?", editalAssuntoId, parte);
        if (!existente.isEmpty() && !"FALHOU".equals(existente.get(0).get("estado"))) {
            throw new ConflictException("Esta parte já tem um PDF anexado.", "ANEXO_JA_EXISTE");
        }
        byte[] bytes;
        try {
            bytes = pdf.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Não foi possível ler o arquivo enviado.", "PDF_INVALIDO", "arquivo");
        }
        int paginas = paginasDe(bytes);

        if (!existente.isEmpty()) {
            arquivos.apagarAnexo((String) existente.get(0).get("arquivo"));
            jdbc.update("DELETE FROM lacuna_anexo WHERE id = ?", existente.get(0).get("id"));
        }
        var relativo = arquivos.salvarAnexo(bytes);
        var nome = pdf.getOriginalFilename() == null || pdf.getOriginalFilename().isBlank() ? "anexo.pdf" : pdf.getOriginalFilename();
        try {
            jdbc.update("INSERT INTO lacuna_anexo (assunto_edital_id, parte, nome_original, arquivo, paginas) VALUES (?, ?, ?, ?, ?)",
                editalAssuntoId, parte, nome.length() > 200 ? nome.substring(0, 200) : nome, relativo, paginas);
        } catch (RuntimeException e) {
            arquivos.apagarAnexo(relativo);
            throw e;
        }
        return material.porAssunto(List.of(assuntoId)).get(assuntoId);
    }

    private static int paginasDe(byte[] bytes) {
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F') {
            throw new ValidationException("O arquivo não é um PDF.", "PDF_INVALIDO", "arquivo");
        }
        try (var documento = Loader.loadPDF(bytes)) {
            int paginas = documento.getNumberOfPages();
            if (paginas < 1 || paginas > MAXIMO_DE_PAGINAS) {
                throw new ValidationException("O PDF tem de ter entre 1 e " + MAXIMO_DE_PAGINAS + " páginas.", "PDF_INVALIDO", "arquivo");
            }
            return paginas;
        } catch (IOException e) {
            throw new ValidationException("Não foi possível abrir o PDF (corrompido ou protegido por senha).", "PDF_INVALIDO", "arquivo");
        }
    }

    @Transactional
    public void remover(Long assuntoId, String parte) {
        var linhas = jdbc.queryForList("""
            SELECT l.id, l.estado, l.arquivo FROM lacuna_anexo l JOIN edital_assunto a ON a.id = l.assunto_edital_id
             WHERE a.assunto_id = ? AND l.parte = ?""", assuntoId, parte);
        if (linhas.isEmpty()) throw new NotFoundException("Esta parte não tem PDF anexado.", "ANEXO_INEXISTENTE");
        if ("PRONTO".equals(linhas.get(0).get("estado"))) {
            throw new ConflictException("O PDF já virou segmentos; segmento se arquiva pelo gesto de sempre.", "ANEXO_JA_PRONTO");
        }
        arquivos.apagarAnexo((String) linhas.get(0).get("arquivo"));
        jdbc.update("DELETE FROM lacuna_anexo WHERE id = ?", linhas.get(0).get("id"));
    }

    private long editalAssuntoId(Long assuntoId) {
        var ids = jdbc.queryForList("SELECT id FROM edital_assunto WHERE assunto_id = ? ORDER BY id DESC", Long.class, assuntoId);
        if (ids.isEmpty()) throw new NotFoundException("Este assunto não nasceu de uma conferência.", "LACUNA_INEXISTENTE");
        return ids.get(0);
    }

    // ---------------------------------------------------------------- planejamento

    @Transactional(readOnly = true)
    public List<AnexoPendente> pendentes() {
        return jdbc.query("""
            SELECT l.id, s.chave_externa, s.nome AS assunto, l.parte, l.arquivo, l.paginas, l.nome_original,
                   COALESCE((SELECT max(g.ordem) FROM segmento g WHERE g.assunto_id = s.id), 0) + 1 AS proxima
              FROM lacuna_anexo l JOIN edital_assunto a ON a.id = l.assunto_edital_id JOIN assunto s ON s.id = a.assunto_id
             WHERE l.estado = 'PENDENTE' ORDER BY l.id""",
            (rs, i) -> new AnexoPendente(rs.getLong("id"), rs.getString("chave_externa"), rs.getString("assunto"), rs.getString("parte"),
                rs.getString("arquivo"), rs.getInt("paginas"), rs.getString("nome_original"), rs.getInt("proxima")));
    }

    @Transactional
    public void pronto(Long id, int segmentos, int minutos) {
        if (jdbc.update("UPDATE lacuna_anexo SET estado = 'PRONTO', segmentos = ?, minutos = ?, erro = NULL, atualizada_em = now() WHERE id = ?",
                segmentos, minutos, id) == 0) {
            throw new NotFoundException("Anexo não encontrado.", "ANEXO_INEXISTENTE");
        }
    }

    @Transactional
    public void falha(Long id, String erro) {
        var texto = erro == null ? "" : erro.length() > 500 ? erro.substring(0, 500) : erro;
        if (jdbc.update("UPDATE lacuna_anexo SET estado = 'FALHOU', erro = ?, atualizada_em = now() WHERE id = ? AND estado <> 'PRONTO'", texto, id) == 0
            && jdbc.queryForObject("SELECT count(*) FROM lacuna_anexo WHERE id = ?", Integer.class, id) == 0) {
            throw new NotFoundException("Anexo não encontrado.", "ANEXO_INEXISTENTE");
        }
    }
}
