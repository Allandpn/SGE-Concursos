package br.com.estudos.reinicio;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.revisao.RefazerEscada;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Reiniciar o progresso (01_DOMINIO §3.27; 04_FRONTEND §8E): de um concurso (a cobertura toda, divididos inclusive) ou de tudo, pela
 * desativação de sessões da Sprint 42 (D-160, D-161), registrada e com desfazer (D-164), depois de digitar REINICIAR (D-165).
 */
@Service
public class ReinicioService {

    public static final String PALAVRA = "REINICIAR";

    public record Dividido(String assunto, List<String> outros) {}

    public record Previa(Long concursoId, String escopo, int sessoes, int assuntos, List<Dividido> divididos) {}

    public record Feito(long id, int sessoes, int assuntos) {}

    public record Linha(long id, Long concursoId, String escopo, int sessoes, int assuntos, Instant feitoEm, Instant desfeitoEm) {}

    private final JdbcTemplate jdbc;
    private final RefazerEscada refazerEscada;

    public ReinicioService(JdbcTemplate jdbc, RefazerEscada refazerEscada) {
        this.jdbc = jdbc;
        this.refazerEscada = refazerEscada;
    }

    /** D-165: o que vai zerar. */
    @Transactional(readOnly = true)
    public Previa previa(Long concursoId) {
        var escopo = escopo(concursoId);
        var alvo = sessoesDoEscopo(concursoId);
        var assuntos = assuntosDe(alvo);
        List<Dividido> divididos = List.of();
        if (concursoId != null && !assuntos.isEmpty()) {
            divididos = jdbc.query("""
                SELECT a.nome, array_agg(DISTINCT c.nome ORDER BY c.nome) AS outros
                  FROM concurso_assunto ca JOIN concurso c ON c.id = ca.concurso_id JOIN assunto a ON a.id = ca.assunto_id
                 WHERE ca.assunto_id = ANY (?) AND ca.concurso_id <> ?
                 GROUP BY a.nome ORDER BY a.nome""",
                (rs, n) -> new Dividido(rs.getString(1), List.of((String[]) rs.getArray(2).getArray())),
                assuntos.toArray(Long[]::new), concursoId);
        }
        return new Previa(concursoId, escopo, alvo.size(), assuntos.size(), divididos);
    }

    /** D-163: tira as sessões do escopo e refaz a escada; D-164: registra o reinício. */
    @Transactional
    public Feito reiniciar(Long concursoId, String confirmacao) {
        if (!PALAVRA.equals(confirmacao == null ? null : confirmacao.trim())) {
            throw new ValidationException("Digite " + PALAVRA + " para confirmar.", "REINICIO_SEM_CONFIRMACAO", "confirmacao");
        }
        escopo(concursoId);
        var alvo = sessoesDoEscopo(concursoId);
        if (alvo.isEmpty()) {
            throw new BusinessRuleException("Não há progresso para reiniciar.", "REINICIO_VAZIO");
        }
        var assuntos = assuntosDe(alvo);
        var id = jdbc.queryForObject("INSERT INTO reinicio (concurso_id, sessoes, assuntos) VALUES (?, ?, ?) RETURNING id", Long.class,
            concursoId, alvo.size(), assuntos.size());
        var ids = alvo.stream().map(s -> s[0]).toArray(Long[]::new);
        jdbc.update("INSERT INTO reinicio_sessao (reinicio_id, sessao_id) SELECT ?, unnest(?::bigint[])", id, ids);
        jdbc.update("UPDATE sessao_registro SET ativo = false, atualizado_em = now() WHERE id = ANY (?)", (Object) ids);
        refazerEscada.refazer(assuntos);
        return new Feito(id, alvo.size(), assuntos.size());
    }

    /** D-164: põe de volta as sessões do reinício que ainda estiverem tiradas e refaz a escada. */
    @Transactional
    public Feito desfazer(long id) {
        var linhas = jdbc.queryForList("SELECT desfeito_em FROM reinicio WHERE id = ? FOR UPDATE", id);
        if (linhas.isEmpty()) throw new NotFoundException("Reinício não encontrado.", "REINICIO_INEXISTENTE");
        if (linhas.get(0).get("desfeito_em") != null) throw new ConflictException("Este reinício já foi desfeito.", "REINICIO_JA_DESFEITO");
        var voltam = jdbc.query("""
            SELECT s.id, s.assunto_id FROM reinicio_sessao rs JOIN sessao_registro s ON s.id = rs.sessao_id
             WHERE rs.reinicio_id = ? AND NOT s.ativo""", (rs, n) -> new Long[] {rs.getLong(1), rs.getLong(2)}, id);
        var assuntos = assuntosDe(voltam);
        if (!voltam.isEmpty()) {
            jdbc.update("UPDATE sessao_registro SET ativo = true, atualizado_em = now() WHERE id = ANY (?)",
                (Object) voltam.stream().map(s -> s[0]).toArray(Long[]::new));
            refazerEscada.refazer(assuntos);
        }
        jdbc.update("UPDATE reinicio SET desfeito_em = now() WHERE id = ?", id);
        return new Feito(id, voltam.size(), assuntos.size());
    }

    @Transactional(readOnly = true)
    public List<Linha> listar() {
        return jdbc.query("""
            SELECT r.id, r.concurso_id, c.nome, r.sessoes, r.assuntos, r.feito_em, r.desfeito_em
              FROM reinicio r LEFT JOIN concurso c ON c.id = r.concurso_id ORDER BY r.feito_em DESC, r.id DESC""",
            (rs, n) -> new Linha(rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3) == null ? "Tudo" : rs.getString(3),
                rs.getInt(4), rs.getInt(5), rs.getTimestamp(6).toInstant(),
                rs.getTimestamp(7) == null ? null : rs.getTimestamp(7).toInstant()));
    }

    private String escopo(Long concursoId) {
        if (concursoId == null) return "Tudo";
        var nomes = jdbc.queryForList("SELECT nome FROM concurso WHERE id = ?", String.class, concursoId);
        if (nomes.isEmpty()) throw new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE");
        return nomes.get(0);
    }

    /** As sessões ativas do escopo: {id, assunto}. */
    private List<Long[]> sessoesDoEscopo(Long concursoId) {
        var sql = "SELECT id, assunto_id FROM sessao_registro WHERE ativo"
            + (concursoId == null ? "" : " AND assunto_id IN (SELECT assunto_id FROM concurso_assunto WHERE concurso_id = ?)");
        var args = concursoId == null ? new Object[0] : new Object[] {concursoId};
        return jdbc.query(sql, (rs, n) -> new Long[] {rs.getLong(1), rs.getLong(2)}, args);
    }

    private static List<Long> assuntosDe(List<Long[]> sessoes) {
        return sessoes.stream().map(s -> s[1]).distinct().sorted().toList();
    }
}
