package br.com.estudos.qconcursos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.arvore.ArvoreService;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * D-173 (docs/SPRINT-45-VINCULO-QCONCURSOS.md): o assunto ligado pela pessoa a nós da árvore do QConcursos. Vale antes dos nós do
 * edital no atalho (D-150) e na captura (D-167). Guarda só o id do nó; nome e caminho vêm da árvore vigente.
 */
@Service
public class VinculoQConcursosService {

    public static final int MAXIMO = 3;

    /** {@code naArvore} falso: o nó saiu da árvore vigente (nome e caminho vazios); o atalho o ignora. */
    public record NoLigado(long noId, String nome, List<String> caminho, boolean naArvore) {}

    private final JdbcTemplate jdbc;
    private final ArvoreService arvore;

    public VinculoQConcursosService(JdbcTemplate jdbc, ArvoreService arvore) {
        this.jdbc = jdbc;
        this.arvore = arvore;
    }

    @Transactional(readOnly = true)
    public List<NoLigado> listar(long assuntoId) {
        exigirAssunto(assuntoId);
        var ids = jdbc.queryForList("SELECT no_id FROM assunto_qconcursos_no WHERE assunto_id = ? ORDER BY ordem", Long.class, assuntoId);
        if (ids.isEmpty()) return List.of();
        var nos = arvore.idDaVigente() == null ? java.util.Map.<Long, br.com.estudos.arvore.ArvoreResponses.No>of() : arvore.nosPorId(ids, null);
        var saida = new ArrayList<NoLigado>();
        for (var id : ids) {
            var no = nos.get(id);
            saida.add(no == null ? new NoLigado(id, null, List.of(), false) : new NoLigado(id, no.nome(), no.caminho(), true));
        }
        return saida;
    }

    /** Troca o vínculo inteiro: 1 a 3 nós que existem na árvore vigente. */
    @Transactional
    public List<NoLigado> ligar(long assuntoId, List<Long> noIds) {
        exigirAssunto(assuntoId);
        var ids = new LinkedHashSet<Long>(noIds == null ? List.of() : noIds);
        ids.remove(null);
        if (ids.isEmpty()) throw new ValidationException("Escolha pelo menos um assunto do QConcursos.", "VINCULO_VAZIO", "noIds");
        if (ids.size() > MAXIMO) throw new ValidationException("No máximo " + MAXIMO + " assuntos do QConcursos por assunto.", "VINCULO_DEMAIS", "noIds");
        var existentes = arvore.nosPorId(ids, null); // sem árvore vigente: 404 ARVORE_NAO_CARREGADA
        for (var id : ids) {
            if (!existentes.containsKey(id)) {
                throw new ValidationException("Este assunto não está na árvore do QConcursos atual.", "NO_INEXISTENTE", "noIds");
            }
        }
        jdbc.update("DELETE FROM assunto_qconcursos_no WHERE assunto_id = ?", assuntoId);
        int ordem = 1;
        for (var id : ids) {
            jdbc.update("INSERT INTO assunto_qconcursos_no (assunto_id, no_id, ordem) VALUES (?, ?, ?)", assuntoId, id, ordem++);
        }
        return listar(assuntoId);
    }

    @Transactional
    public void tirar(long assuntoId) {
        exigirAssunto(assuntoId);
        jdbc.update("DELETE FROM assunto_qconcursos_no WHERE assunto_id = ?", assuntoId);
    }

    private void exigirAssunto(long assuntoId) {
        if (jdbc.queryForList("SELECT 1 FROM assunto WHERE id = ?", Integer.class, assuntoId).isEmpty()) {
            throw new NotFoundException("Assunto não encontrado.", "ASSUNTO_INEXISTENTE");
        }
    }
}
