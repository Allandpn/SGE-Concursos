package br.com.estudos.regua;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

/** D-136: a conta da nota em pontos, sem banco. */
class ReguaTest {

    static Regua.Linha linha(int certas, int erradas, int brancos, String pontos) {
        return new Regua.Linha(1L, "D", certas, erradas, brancos, pontos == null ? null : new BigDecimal(pontos));
    }

    @Test
    void d136_semReguaSaoOsAcertos() {
        var n = Regua.pontuar(List.of(linha(7, 2, 1, null), linha(3, 0, 0, null)), false, null);
        assertThat(n.pontos()).isEqualByComparingTo("10");
        assertThat(n.maximo()).isEqualByComparingTo("13");
        assertThat(n.diferenca()).isNull();
    }

    @Test
    void d136_pesoPorDisciplina_eErradaAnulaCerta_brancoNaoDesconta() {
        var n = Regua.pontuar(List.of(linha(10, 4, 6, "2"), linha(5, 5, 0, "1")), true, new BigDecimal("15"));
        assertThat(n.porDisciplina().get(0).pontos()).isEqualByComparingTo("12"); // 20 − 8; os 6 brancos não descontam
        assertThat(n.porDisciplina().get(1).pontos()).isEqualByComparingTo("0");
        assertThat(n.pontos()).isEqualByComparingTo("12");
        assertThat(n.diferenca()).isEqualByComparingTo("-3");
    }

    @Test
    void d136_globalNuncaAbaixoDeZero() {
        var n = Regua.pontuar(List.of(linha(1, 9, 0, "1")), true, BigDecimal.TEN);
        assertThat(n.porDisciplina().get(0).pontos()).isEqualByComparingTo("-8");
        assertThat(n.pontos()).isEqualByComparingTo("0");
    }
}
