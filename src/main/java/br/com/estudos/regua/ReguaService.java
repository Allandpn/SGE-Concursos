package br.com.estudos.regua;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/** A régua da prova (D-135) e a nota em pontos (D-136); docs/SPRINT-23-REGISTRO-E-PROVA-REAL.md §2. */
@Service
public class ReguaService {

    public record DisciplinaDaRegua(long disciplinaId, String nome, BigDecimal pontos) {}

    public record ReguaDoConcurso(boolean erradaAnulaCerta, BigDecimal notaCorte, List<DisciplinaDaRegua> disciplinas) {}

    public record PontosDoSimulado(long simuladoId, String data, Regua.Nota nota) {}

    private final JdbcTemplate jdbc;

    public ReguaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public ReguaDoConcurso regua(long concursoId) {
        var concurso = jdbc.query("SELECT errada_anula_certa, nota_corte FROM concurso WHERE id = ?",
            (rs, n) -> new Object[] {rs.getBoolean(1), rs.getBigDecimal(2)}, concursoId);
        if (concurso.isEmpty()) {
            throw new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE");
        }
        var disciplinas = jdbc.query("""
            SELECT d.id, d.nome, coalesce(p.pontos, 1) AS pontos
            FROM disciplina d
            LEFT JOIN concurso_pontuacao p ON p.disciplina_id = d.id AND p.concurso_id = ?
            WHERE d.id IN (SELECT ca.disciplina_id FROM concurso_assunto ca WHERE ca.concurso_id = ?)
            ORDER BY d.nome""",
            (rs, n) -> new DisciplinaDaRegua(rs.getLong("id"), rs.getString("nome"), rs.getBigDecimal("pontos").stripTrailingZeros()),
            concursoId, concursoId);
        return new ReguaDoConcurso((Boolean) concurso.get(0)[0], (BigDecimal) concurso.get(0)[1], disciplinas);
    }

    /** D-135: salva a régua inteira; nada calculado é guardado. */
    @Transactional
    public ReguaDoConcurso salvar(long concursoId, ReguaDoConcurso regua) {
        if (regua.notaCorte() != null && regua.notaCorte().signum() < 0) {
            throw new ValidationException("A nota de corte não pode ser negativa.", "NOTA_CORTE_INVALIDA", "notaCorte");
        }
        var n = jdbc.update("UPDATE concurso SET errada_anula_certa = ?, nota_corte = ? WHERE id = ?",
            regua.erradaAnulaCerta(), regua.notaCorte(), concursoId);
        if (n == 0) {
            throw new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE");
        }
        for (var d : regua.disciplinas() == null ? List.<DisciplinaDaRegua>of() : regua.disciplinas()) {
            if (d.pontos() == null || d.pontos().signum() <= 0) {
                throw new ValidationException("Os pontos por questão precisam ser positivos.", "PONTOS_INVALIDOS", "pontos");
            }
            jdbc.update("""
                INSERT INTO concurso_pontuacao (concurso_id, disciplina_id, pontos) VALUES (?, ?, ?)
                ON CONFLICT (concurso_id, disciplina_id) DO UPDATE SET pontos = EXCLUDED.pontos""",
                concursoId, d.disciplinaId(), d.pontos());
        }
        return regua(concursoId);
    }

    /** D-136: a prova resolvida (a primeira tentativa entregue), pela régua do concurso da prova. Vazio sem tentativa entregue. */
    @Transactional(readOnly = true)
    public Optional<Regua.Nota> pontosDaProva(long provaId) {
        var concurso = jdbc.queryForList("SELECT concurso_id FROM prova WHERE id = ?", Long.class, provaId);
        if (concurso.isEmpty()) {
            throw new NotFoundException("Prova não encontrada.", "PROVA_INEXISTENTE");
        }
        var tentativa = jdbc.queryForList("""
            SELECT id FROM tentativa_prova WHERE prova_id = ? AND entregue_em IS NOT NULL ORDER BY entregue_em, id LIMIT 1""", Long.class, provaId);
        if (tentativa.isEmpty()) {
            return Optional.empty();
        }
        long concursoId = concurso.get(0);
        // Só questão avaliável com assunto, como na nota de acertos: fora do edital, anulada e sem texto ficam fora.
        var linhas = jdbc.query("""
            SELECT d.id, d.nome, p.pontos,
                   count(*) FILTER (WHERE r.alternativa IS NOT NULL AND r.alternativa = q.resposta_gabarito) AS certas,
                   count(*) FILTER (WHERE r.alternativa IS NOT NULL AND r.alternativa <> q.resposta_gabarito) AS erradas,
                   count(*) FILTER (WHERE r.alternativa IS NULL) AS brancos
            FROM questao q
            JOIN assunto a ON a.id = q.assunto_id
            JOIN disciplina d ON d.id = a.disciplina_id
            LEFT JOIN resposta_prova r ON r.questao_id = q.id AND r.tentativa_id = ?
            LEFT JOIN concurso_pontuacao p ON p.disciplina_id = d.id AND p.concurso_id = ?
            WHERE q.prova_id = ? AND q.motivo_nao_avaliavel IS NULL AND q.resposta_gabarito IS NOT NULL
            GROUP BY d.id, d.nome, p.pontos ORDER BY d.nome""",
            (rs, n) -> new Regua.Linha(rs.getLong("id"), rs.getString("nome"), rs.getInt("certas"), rs.getInt("erradas"), rs.getInt("brancos"),
                rs.getBigDecimal("pontos")),
            tentativa.get(0), concursoId, provaId);
        var regua = regua(concursoId);
        return Optional.of(Regua.pontuar(linhas, regua.erradaAnulaCerta(), regua.notaCorte()));
    }

    /** D-136: cada simulado pela régua do concurso-alvo; o simulado não guarda brancos, então o que não é acerto conta como erro. */
    @Transactional(readOnly = true)
    public List<PontosDoSimulado> pontosDosSimulados() {
        var alvo = jdbc.queryForList("SELECT id FROM concurso WHERE ativo LIMIT 1", Long.class);
        if (alvo.isEmpty()) {
            return List.of();
        }
        long concursoId = alvo.get(0);
        var regua = regua(concursoId);
        var simulados = jdbc.query("SELECT id, data FROM simulado ORDER BY data DESC, id DESC",
            (rs, n) -> new Object[] {rs.getLong("id"), rs.getString("data")});
        return simulados.stream().map(sm -> {
            var linhas = jdbc.query("""
                SELECT d.id, d.nome, p.pontos, sum(r.questoes_corretas) AS certas, sum(r.questoes_total - r.questoes_corretas) AS erradas
                FROM resultado_simulado r JOIN disciplina d ON d.id = r.disciplina_id
                LEFT JOIN concurso_pontuacao p ON p.disciplina_id = d.id AND p.concurso_id = ?
                WHERE r.simulado_id = ? GROUP BY d.id, d.nome, p.pontos ORDER BY d.nome""",
                (rs, n) -> new Regua.Linha(rs.getLong("id"), rs.getString("nome"), rs.getInt("certas"), rs.getInt("erradas"), 0, rs.getBigDecimal("pontos")),
                concursoId, sm[0]);
            return new PontosDoSimulado((Long) sm[0], (String) sm[1], Regua.pontuar(linhas, regua.erradaAnulaCerta(), regua.notaCorte()));
        }).toList();
    }

    /** D-133: o formato do lote rápido — do assunto, senão da prova mais recente do alvo, senão do histórico, senão múltipla escolha. */
    @Transactional(readOnly = true)
    public String formatoSugerido(long assuntoId) {
        return primeiro("SELECT formato FROM sessao WHERE assunto_id = ? AND tipo = 'QUESTOES' AND ativo ORDER BY data DESC, id DESC LIMIT 1", assuntoId)
            .or(() -> primeiro("""
                SELECT p.formato FROM prova p JOIN concurso c ON c.id = p.concurso_id WHERE c.ativo AND p.formato IS NOT NULL
                ORDER BY p.id DESC LIMIT 1"""))
            .or(() -> primeiro("SELECT formato FROM sessao WHERE tipo = 'QUESTOES' AND ativo ORDER BY data DESC, id DESC LIMIT 1"))
            .orElse("MULTIPLA_ESCOLHA");
    }

    private Optional<String> primeiro(String sql, Object... args) {
        return jdbc.queryForList(sql, String.class, args).stream().findFirst();
    }
}
