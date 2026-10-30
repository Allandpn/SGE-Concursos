package br.com.estudos.foco;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.estudos.foco.FaseFoco.Especie;
import br.com.estudos.push.PushService;
import br.com.estudos.push.PushService.Aviso;

/**
 * Fim de fase do Pomodoro (D-104, ADR-050). Encerra o que venceu e avisa, por Web Push, só os aparelhos que pediram o aviso de fase,
 * uma vez por fase, e só de fase que a própria pessoa iniciou. Nunca lembra de estudar, nunca cobra, nunca conta dias.
 */
@Component
@EnableScheduling
public class AvisoDeFase {

    /** Fase que venceu há mais que isto não avisa mais: o aviso fora de hora só atrapalha. */
    static final Duration VALIDADE = Duration.ofMinutes(10);

    private final FocoService foco;
    private final PushService push;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AvisoDeFase(FocoService foco, PushService push, JdbcTemplate jdbc, Clock clock) {
        this.foco = foco;
        this.push = push;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** A cada 15 s; o valor "-" desliga o agendamento (os testes chamam {@link #varrer()} com o relógio na mão). */
    @Scheduled(cron = "${estudos.foco.varredura-cron:*/15 * * * * *}")
    public void varrer() {
        foco.encerrarVencidas();
        var agora = Instant.now(clock);
        // Inclui as que a consulta da tela encerrou antes da varredura: o aviso é de quem venceu, não de quem percebeu primeiro.
        List<Object[]> pendentes = jdbc.query("SELECT id, especie, fim FROM fase_foco WHERE motivo_fim = 'VENCIDA' AND aviso_fim_em IS NULL",
            (rs, n) -> new Object[] { rs.getLong("id"), rs.getString("especie"), rs.getObject("fim", OffsetDateTime.class).toInstant() });
        for (var p : pendentes) {
            long id = (Long) p[0];
            // Reivindica a fase: a marca é a trava de "uma vez por fase", mesmo sem nenhuma inscrição.
            if (jdbc.update("UPDATE fase_foco SET aviso_fim_em = ? WHERE id = ? AND aviso_fim_em IS NULL",
                OffsetDateTime.ofInstant(agora, ZoneOffset.UTC), id) != 1) continue;
            if (Duration.between((Instant) p[2], agora).compareTo(VALIDADE) > 0) continue;
            var foco = Especie.valueOf((String) p[1]) == Especie.FOCO;
            push.enviarDeFase(new Aviso("Pomodoro", foco ? "O foco terminou." : "A pausa terminou.", "/"));
        }
    }
}
