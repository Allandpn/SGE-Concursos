package br.com.estudos.revisao;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.sessao.SessaoRepository;
import jakarta.persistence.EntityManager;

/**
 * D-161 (01_DOMINIO §3.26; ADR-062): refaz a escada de um assunto só com as sessões ativas, pelas mesmas regras do registro.
 * As revisões do assunto (derivadas das sessões) são apagadas; as sessões ativas são desligadas e religadas uma a uma, na ordem
 * (data, id), e cada uma passa por {@link RevisaoService#processarSessao} vendo só as anteriores — como no registro original.
 */
@Component
public class RefazerEscada {

    private record Pendente(Long origem, int nivel, LocalDate data, LocalDate antesRetaFinal) {}

    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final RevisaoService revisaoService;
    private final SessaoRepository sessaoRepository;

    public RefazerEscada(JdbcTemplate jdbc, EntityManager em, RevisaoService revisaoService, SessaoRepository sessaoRepository) {
        this.jdbc = jdbc;
        this.em = em;
        this.revisaoService = revisaoService;
        this.sessaoRepository = sessaoRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void refazer(Collection<Long> assuntoIds) {
        for (var assuntoId : assuntoIds) refazer(assuntoId);
    }

    private void refazer(long assuntoId) {
        em.flush();
        var antes = pendente(assuntoId);
        var ativas = jdbc.queryForList("SELECT id FROM sessao_registro WHERE assunto_id = ? AND ativo ORDER BY data, id", Long.class, assuntoId);
        jdbc.update("DELETE FROM revisao WHERE assunto_id = ?", assuntoId);
        jdbc.update("UPDATE sessao_registro SET ativo = false WHERE assunto_id = ? AND ativo", assuntoId);
        em.clear();
        for (var id : ativas) {
            jdbc.update("UPDATE sessao_registro SET ativo = true WHERE id = ?", id);
            revisaoService.processarSessao(sessaoRepository.findById(id).orElseThrow());
            em.flush();
            em.clear();
        }
        // A pendente refeita que é a mesma de antes (mesma origem, mesmo nível) guarda a data que tinha: a antecipação da reta
        // final (D-143) e a data da reativação (D-49).
        var depois = pendente(assuntoId);
        if (antes != null && depois != null && antes.origem() != null && antes.origem().equals(depois.origem())
                && antes.nivel() == depois.nivel()) {
            jdbc.update("""
                UPDATE revisao SET data_prevista = ?, data_prevista_antes_reta_final = ?, atualizado_em = now()
                WHERE assunto_id = ? AND situacao = 'PENDENTE'""", antes.data(), antes.antesRetaFinal(), assuntoId);
        }
        // D-17: num assunto (ou disciplina) arquivado, a pendente refeita fica cancelada.
        var arquivado = jdbc.queryForObject("""
            SELECT NOT (a.ativo AND d.ativo) FROM assunto a JOIN disciplina d ON d.id = a.disciplina_id WHERE a.id = ?""",
            Boolean.class, assuntoId);
        if (Boolean.TRUE.equals(arquivado)) revisaoService.cancelarPendentePorAssunto(assuntoId);
    }

    private Pendente pendente(long assuntoId) {
        List<Pendente> l = jdbc.query("""
            SELECT sessao_origem_id, nivel, data_prevista, data_prevista_antes_reta_final FROM revisao
            WHERE assunto_id = ? AND situacao = 'PENDENTE'""",
            (rs, n) -> new Pendente(rs.getObject(1, Long.class), rs.getInt(2), rs.getObject(3, LocalDate.class),
                rs.getObject(4, LocalDate.class)),
            assuntoId);
        return l.isEmpty() ? null : l.get(0);
    }
}
