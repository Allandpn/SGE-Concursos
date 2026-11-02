package br.com.estudos.retafinal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A reta final ativa (D-141; docs/SPRINT-21-RETA-FINAL.md §2): aberta, do concurso-alvo, com a entrada depois da última ativação
 * dele (trocar de alvo encerra sem escrita) e hoje até a data da prova. Só leitura; sem dependência do plano, para o
 * {@code FrenteService} poder usá-la.
 */
@Component
public class RetaFinalConsulta {

    public record Ativa(long id, long concursoId, LocalDate dataProva, long dias) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RetaFinalConsulta(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<Ativa> ativa() {
        var hoje = LocalDate.now(clock);
        return jdbc.query("""
            SELECT r.id, c.id, c.data_prova FROM reta_final r JOIN concurso c ON c.id = r.concurso_id
            WHERE r.saiu_em IS NULL AND c.ativo AND c.data_prova IS NOT NULL AND c.data_prova >= ?
              AND (c.ativado_em IS NULL OR r.entrou_em >= c.ativado_em)""",
            (rs, n) -> {
                var prova = rs.getObject(3, LocalDate.class);
                return new Ativa(rs.getLong(1), rs.getLong(2), prova, java.time.temporal.ChronoUnit.DAYS.between(hoje, prova));
            }, hoje).stream().findFirst();
    }
}
