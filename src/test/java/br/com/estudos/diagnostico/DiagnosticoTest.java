package br.com.estudos.diagnostico;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/** D-138 e D-139 (docs/SPRINT-20-DIAGNOSTICO-EDITAL.md §4). */
class DiagnosticoTest {

    @Test
    void d138_valorPorAssunto_pontosVezesQuestoes_divididoPelosAssuntos() {
        var v = Diagnostico.valores(List.of(
            new Diagnostico.Disciplina(1, new BigDecimal("2"), 10, List.of(11L, 12L)),
            new Diagnostico.Disciplina(2, BigDecimal.ONE, 5, List.of(21L))));
        assertThat(v.get(11L)).isEqualByComparingTo("10");
        assertThat(v.get(21L)).isEqualByComparingTo("5");
        assertThat(Diagnostico.percentual(v.get(11L), new BigDecimal("25"))).isEqualTo(40);
    }

    @Test
    void d138_faltandoQuestoesDeAlguma_soOsPontosPorQuestao_eSemReguaTodasIguais() {
        var comRegua = Diagnostico.valores(List.of(
            new Diagnostico.Disciplina(1, new BigDecimal("2"), 10, List.of(11L, 12L)),
            new Diagnostico.Disciplina(2, BigDecimal.ONE, null, List.of(21L))));
        assertThat(comRegua.get(11L)).isEqualByComparingTo("1");
        assertThat(comRegua.get(21L)).isEqualByComparingTo("1");
        var semRegua = Diagnostico.valores(List.of(
            new Diagnostico.Disciplina(1, BigDecimal.ONE, null, List.of(11L, 12L, 13L, 14L)),
            new Diagnostico.Disciplina(2, BigDecimal.ONE, null, List.of(21L))));
        assertThat(semRegua.get(11L).multiply(BigDecimal.valueOf(4))).isEqualByComparingTo(semRegua.get(21L));
    }

    @Test
    void d139_cabemSemanasVezesRitmo_eTodosVistosEm() {
        var hoje = LocalDate.of(2026, 11, 1);
        var c = Diagnostico.cabe(71, hoje, hoje.plusDays(131), new BigDecimal("3.5"));
        assertThat(c.dias()).isEqualTo(131);
        assertThat(c.cabem()).isEqualTo(65); // 131 / 7 × 3,5 = 65,5
        assertThat(c.todosVistosEm()).isEqualTo(hoje.plusDays(142)); // ⌈71 × 7 / 3,5⌉
        assertThat(Diagnostico.cabe(10, hoje, hoje.plusDays(365), BigDecimal.ONE).cabem()).isEqualTo(10);
    }

    @Test
    void d139_semRitmo_naoCabeNada_eSemDataDeTodosVistos() {
        var hoje = LocalDate.of(2026, 11, 1);
        var c = Diagnostico.cabe(5, hoje, hoje.plusDays(30), BigDecimal.ZERO);
        assertThat(c.cabem()).isZero();
        assertThat(c.todosVistosEm()).isNull();
        assertThat(Diagnostico.ritmo(14)).isEqualByComparingTo("3.5");
        assertThat(Diagnostico.ritmo(0)).isEqualByComparingTo("0");
    }
}
