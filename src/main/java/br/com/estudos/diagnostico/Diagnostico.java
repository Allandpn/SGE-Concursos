package br.com.estudos.diagnostico;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * As contas puras do diagnóstico do edital (01_DOMINIO §3.21; docs/SPRINT-20-DIAGNOSTICO-EDITAL.md §2). Nada é guardado (D-16).
 */
public final class Diagnostico {

    private Diagnostico() {}

    /** Uma disciplina da cobertura: pontos por questão da régua, questões do edital (nulo = desconhecido) e os assuntos dela. */
    public record Disciplina(long disciplinaId, BigDecimal pontos, Integer questoes, List<Long> assuntos) {}

    /**
     * D-138: valor de cada assunto = pontos × questões da disciplina ÷ assuntos dela. Se alguma disciplina não tem número de
     * questões, todas valem igual (só os pontos por questão da régua contam, uma questão cada), para não misturar escalas.
     */
    public static Map<Long, BigDecimal> valores(List<Disciplina> disciplinas) {
        var todasComQuestoes = !disciplinas.isEmpty() && disciplinas.stream().allMatch(d -> d.questoes() != null && d.questoes() > 0);
        var valores = new HashMap<Long, BigDecimal>();
        for (var d : disciplinas) {
            if (d.assuntos().isEmpty()) {
                continue;
            }
            var pontos = d.pontos() == null ? BigDecimal.ONE : d.pontos();
            var daDisciplina = todasComQuestoes ? pontos.multiply(BigDecimal.valueOf(d.questoes())) : pontos;
            var porAssunto = daDisciplina.divide(BigDecimal.valueOf(d.assuntos().size()), 10, RoundingMode.HALF_UP);
            d.assuntos().forEach(a -> valores.put(a, porAssunto));
        }
        return valores;
    }

    /** Percentual inteiro de {@code parte} em {@code total}; zero sem total. */
    public static int percentual(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return 0;
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }

    /** O resultado de "o que cabe": quantos cabem e a data em que todos estariam vistos (nula com ritmo zero). */
    public record Cabe(long dias, int cabem, LocalDate todosVistosEm) {}

    /**
     * D-139: semanas até a data (dias ÷ 7, fracionário) × ritmo (assuntos novos por semana) = quantos dos nunca vistos cabem, no
     * máximo todos. Todos vistos em hoje + ⌈nunca ÷ ritmo × 7⌉ dias.
     */
    public static Cabe cabe(int nuncaVistos, LocalDate hoje, LocalDate data, BigDecimal ritmo) {
        var dias = Math.max(0, ChronoUnit.DAYS.between(hoje, data));
        if (ritmo == null || ritmo.signum() <= 0) {
            return new Cabe(dias, 0, null);
        }
        var cabem = BigDecimal.valueOf(dias).multiply(ritmo).divide(BigDecimal.valueOf(7), 0, RoundingMode.FLOOR).intValue();
        var diasParaTodos = BigDecimal.valueOf(nuncaVistos).multiply(BigDecimal.valueOf(7)).divide(ritmo, 0, RoundingMode.CEILING).longValue();
        return new Cabe(dias, Math.min(cabem, nuncaVistos), hoje.plusDays(diasParaTodos));
    }

    /** D-139: ritmo medido = assuntos com a primeira sessão nas últimas 4 semanas ÷ 4, com uma casa. */
    public static BigDecimal ritmo(int assuntosNovosEm4Semanas) {
        return BigDecimal.valueOf(assuntosNovosEm4Semanas).divide(BigDecimal.valueOf(4), 1, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
