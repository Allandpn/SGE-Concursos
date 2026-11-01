package br.com.estudos.proprio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * O PDF próprio (01_DOMINIO §3.19, D-129 a D-132; docs/SPRINT-33-PDF-PROPRIO.md; ADR-054): o SGE corta em blocos na própria
 * requisição e os grava no volume `editais`, servidos em `/material/proprios/**`.
 */
@Service
public class PdfProprioService {

    private static final Logger LOG = LoggerFactory.getLogger(PdfProprioService.class);
    static final long MAXIMO_BYTES = 50L * 1024 * 1024;
    /** Menos que isso de texto útil no documento: escaneado (D-130). */
    static final int MINIMO_TEXTO = 300;

    public enum Tipo {
        APOSTILA(2), LEI_SECA(3);

        final int minutosPorPagina;

        Tipo(int minutosPorPagina) {
            this.minutosPorPagina = minutosPorPagina;
        }
    }

    public record Resultado(long id, String nome, int blocos, int minutos, boolean semTexto) {}

    public record PdfDoAssunto(long id, String nome, String tipo, int paginas, boolean semTexto, OffsetDateTime criadoEm, List<Long> segmentos) {}

    private final JdbcTemplate jdbc;
    private final Clock relogio;
    private final Path raiz;

    public PdfProprioService(JdbcTemplate jdbc, Clock relogio, @Value("${estudos.editais.dir:/dados/editais}") String editais) {
        this.jdbc = jdbc;
        this.relogio = relogio;
        this.raiz = Path.of(editais).toAbsolutePath().normalize().resolve("proprios");
    }

    @Transactional
    public Resultado enviar(long assuntoId, MultipartFile arquivo, String tipoTexto) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM assunto WHERE id = ?)", Boolean.class, assuntoId))) {
            throw new NotFoundException("Assunto não encontrado.", "ASSUNTO_NAO_ENCONTRADO");
        }
        Tipo tipo;
        try {
            tipo = Tipo.valueOf(tipoTexto == null ? "" : tipoTexto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Escolha o tipo de leitura: apostila ou lei seca.", "TIPO_INVALIDO", "tipo");
        }
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ValidationException("Escolha o PDF.", "PDF_INVALIDO", "arquivo");
        }
        if (arquivo.getSize() > MAXIMO_BYTES) {
            throw new ValidationException("O PDF passa de 50 MB.", "PDF_GRANDE_DEMAIS", "arquivo");
        }
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Não foi possível ler o arquivo.", "PDF_INVALIDO", "arquivo");
        }
        var nome = nomeLimpo(arquivo.getOriginalFilename());
        try (var documento = Loader.loadPDF(bytes)) {
            int paginas = documento.getNumberOfPages();
            if (paginas < 1) {
                throw new ValidationException("O PDF não tem páginas.", "PDF_INVALIDO", "arquivo");
            }
            boolean semTexto = semTexto(documento);
            long id = jdbc.queryForObject("""
                INSERT INTO pdf_proprio (assunto_id, nome, tipo, paginas, sem_texto, criado_em) VALUES (?, ?, ?, ?, ?, ?) RETURNING id""",
                Long.class, assuntoId, nome, tipo.name(), paginas, semTexto, agora());
            var pasta = raiz.resolve(Long.toString(id));
            Files.createDirectories(pasta);
            Files.write(pasta.resolve("original.pdf"), bytes);

            var blocos = CorteDoPdf.cortar(paginas, tipo.minutosPorPagina, iniciosDeSecao(documento));
            int ordem = jdbc.queryForObject("SELECT coalesce(max(ordem), 0) FROM segmento WHERE assunto_id = ? AND arquivado_em IS NULL",
                Integer.class, assuntoId);
            int minutos = 0;
            int n = 0;
            for (var bloco : blocos) {
                n++;
                var arquivoBloco = String.format("segmento-%02d.pdf", n);
                try (var parte = new PDDocument()) {
                    for (int p = bloco.inicio(); p <= bloco.fim(); p++) {
                        parte.importPage(documento.getPage(p - 1));
                    }
                    parte.save(pasta.resolve(arquivoBloco).toFile());
                }
                int min = bloco.paginas() * tipo.minutosPorPagina;
                minutos += min;
                jdbc.update("""
                    INSERT INTO segmento (assunto_id, chave_externa, ordem, arquivo, pagina_inicial, pagina_final, tempo_estimado_min, pdf_proprio_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                    assuntoId, "proprio-" + id + "-" + n, ordem + n, "/material/proprios/" + id + "/" + arquivoBloco,
                    bloco.inicio(), bloco.fim(), min, id);
            }
            LOG.info("pdf próprio {} ({}) no assunto {}: {} páginas em {} blocos{}", id, nome, assuntoId, paginas, blocos.size(),
                semTexto ? ", sem texto" : "");
            return new Resultado(id, nome, blocos.size(), minutos, semTexto);
        } catch (InvalidPasswordException e) {
            throw new ValidationException("O PDF está protegido por senha.", "PDF_INVALIDO", "arquivo");
        } catch (IOException e) {
            throw new ValidationException("O arquivo não é um PDF válido.", "PDF_INVALIDO", "arquivo");
        }
    }

    @Transactional(readOnly = true)
    public List<PdfDoAssunto> listar(long assuntoId) {
        return jdbc.query("""
            SELECT p.id, p.nome, p.tipo, p.paginas, p.sem_texto, p.criado_em,
                   coalesce(array_agg(s.id ORDER BY s.ordem) FILTER (WHERE s.id IS NOT NULL), '{}') AS segmentos
            FROM pdf_proprio p LEFT JOIN segmento s ON s.pdf_proprio_id = p.id AND s.arquivado_em IS NULL
            WHERE p.assunto_id = ? AND p.retirado_em IS NULL
            GROUP BY p.id ORDER BY p.criado_em, p.id""",
            (rs, i) -> {
                var ids = new ArrayList<Long>();
                for (var v : (Long[]) rs.getArray("segmentos").getArray()) {
                    ids.add(v);
                }
                return new PdfDoAssunto(rs.getLong("id"), rs.getString("nome"), rs.getString("tipo"), rs.getInt("paginas"),
                    rs.getBoolean("sem_texto"), rs.getObject("criado_em", OffsetDateTime.class), ids);
            },
            assuntoId);
    }

    /** D-132: arquiva de uma vez os blocos deste PDF; nada é apagado. */
    @Transactional
    public void tirar(long id) {
        var n = jdbc.update("UPDATE pdf_proprio SET retirado_em = ? WHERE id = ? AND retirado_em IS NULL", agora(), id);
        if (n == 0 && !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pdf_proprio WHERE id = ?)", Boolean.class, id))) {
            throw new NotFoundException("PDF não encontrado.", "PDF_NAO_ENCONTRADO");
        }
        jdbc.update("UPDATE segmento SET arquivado_em = ? WHERE pdf_proprio_id = ? AND arquivado_em IS NULL", agora(), id);
    }

    private static boolean semTexto(PDDocument documento) throws IOException {
        var leitor = new PDFTextStripper();
        leitor.setEndPage(Math.min(documento.getNumberOfPages(), 40)); // amostra suficiente para decidir
        return leitor.getText(documento).replaceAll("\\s+", "").length() < MINIMO_TEXTO;
    }

    /** As páginas (1..n) onde começa alguma seção, pelos marcadores do PDF, em qualquer nível. */
    private static TreeSet<Integer> iniciosDeSecao(PDDocument documento) {
        var inicios = new TreeSet<Integer>();
        var raiz = documento.getDocumentCatalog().getDocumentOutline();
        if (raiz != null) {
            coletar(documento, raiz, inicios, 0);
        }
        return inicios;
    }

    private static void coletar(PDDocument documento, PDOutlineNode no, TreeSet<Integer> inicios, int profundidade) {
        if (profundidade > 8) {
            return;
        }
        for (var item : no.children()) {
            try {
                PDPage pagina = item.findDestinationPage(documento);
                if (pagina != null) {
                    int indice = documento.getPages().indexOf(pagina);
                    if (indice >= 0) {
                        inicios.add(indice + 1);
                    }
                } else if (item.getDestination() instanceof PDPageDestination d && d.retrievePageNumber() >= 0) {
                    inicios.add(d.retrievePageNumber() + 1);
                }
            } catch (IOException e) {
                // marcador quebrado: ignora, o corte cai no tempo
            }
            coletar(documento, item, inicios, profundidade + 1);
        }
    }

    private static String nomeLimpo(String original) {
        var nome = original == null || original.isBlank() ? "meu-pdf.pdf" : Path.of(original).getFileName().toString();
        return nome.length() > 255 ? nome.substring(0, 255) : nome;
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
    }
}
