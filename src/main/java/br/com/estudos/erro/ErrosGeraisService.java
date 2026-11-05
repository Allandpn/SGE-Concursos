package br.com.estudos.erro;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.NotFoundException;

/**
 * A lista geral de erros (04_FRONTEND §8B, "As demais telas"; Sprint 40): todos os erros dos assuntos ativos, do mais novo ao mais
 * antigo, com filtros por disciplina, causa e "só não resolvidos"; o erro de sessão tirada da escada fica de fora (D-160).
 */
@Service
public class ErrosGeraisService {

    public record ErroGeral(long id, long assuntoId, String assunto, long disciplinaId, String disciplina, String descricao, String causa,
        String confianca, boolean resolvido, String questaoExterna, Instant criadoEm) {}

    static final int LIMITE = 300;

    private final JdbcTemplate jdbc;

    public ErrosGeraisService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<ErroGeral> listar(Long disciplinaId, String causa, boolean soNaoResolvidos) {
        var sql = new StringBuilder("""
            SELECT e.id, e.assunto_id, a.nome AS assunto, d.id AS disciplina_id, d.nome AS disciplina, e.descricao, e.causa, e.confianca,
                   e.resolvido, e.questao_externa, e.criado_em
              FROM erro e JOIN assunto a ON a.id = e.assunto_id JOIN disciplina d ON d.id = a.disciplina_id
             WHERE a.ativo AND d.ativo AND (e.sessao_id IS NULL OR EXISTS (SELECT 1 FROM sessao s WHERE s.id = e.sessao_id))""");
        var args = new ArrayList<Object>();
        if (disciplinaId != null) { sql.append(" AND d.id = ?"); args.add(disciplinaId); }
        if (causa != null && !causa.isBlank()) { sql.append(" AND e.causa = ?"); args.add(causa); }
        if (soNaoResolvidos) sql.append(" AND NOT e.resolvido");
        sql.append(" ORDER BY e.criado_em DESC, e.id DESC LIMIT " + LIMITE);
        return jdbc.query(sql.toString(), (rs, n) -> new ErroGeral(rs.getLong("id"), rs.getLong("assunto_id"), rs.getString("assunto"),
            rs.getLong("disciplina_id"), rs.getString("disciplina"), rs.getString("descricao"), rs.getString("causa"), rs.getString("confianca"),
            rs.getBoolean("resolvido"), rs.getString("questao_externa"), rs.getTimestamp("criado_em").toInstant()), args.toArray());
    }

    /** Marcar (ou desmarcar) como resolvido: o erro continua guardado. */
    @Transactional
    public void resolver(long id, boolean resolvido) {
        if (jdbc.update("UPDATE erro SET resolvido = ?, atualizado_em = now() WHERE id = ?", resolvido, id) == 0) {
            throw new NotFoundException("Erro não encontrado.", "ERRO_INEXISTENTE");
        }
    }
}
