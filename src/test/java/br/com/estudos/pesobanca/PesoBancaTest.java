package br.com.estudos.pesobanca;

import static br.com.estudos.shared.enums.TipoPeso.ALTO;
import static br.com.estudos.shared.enums.TipoPeso.BAIXO;
import static br.com.estudos.shared.enums.TipoPeso.MEDIO;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.estudos.pesobanca.PesoBanca.Linha;

/** D-145 (a banca) e D-146 (a proposta) — docs/SPRINT-22-PESO-PELA-BANCA.md §4. */
class PesoBancaTest {

    @Test
    void d145_mesmaBanca_semAcentoSemCaixa_eCespeIgualCebraspe() {
        assertThat(PesoBanca.mesmaBanca("CESPE", "Cebraspe (CESPE)")).isTrue();
        assertThat(PesoBanca.mesmaBanca("cebraspe", "CEBRASPE")).isTrue();
        assertThat(PesoBanca.mesmaBanca("Fundação Getúlio Vargas", "FUNDACAO GETULIO VARGAS")).isTrue();
        assertThat(PesoBanca.mesmaBanca("FGV", "FCC")).isFalse();
        assertThat(PesoBanca.mesmaBanca(null, null)).isFalse();
    }

    @Test
    void d146_terços_sobeDesce_eAltoNaoSobeBaixoNaoDesce() {
        var p = PesoBanca.propor(List.of(new Linha(1, MEDIO, 20), new Linha(2, MEDIO, 8), new Linha(3, BAIXO, 4), new Linha(4, ALTO, 0)));
        assertThat(p.get(1L)).isEqualTo(ALTO); // mais que 3 dos 3 outros
        assertThat(p.get(2L)).isEqualTo(ALTO); // mais que 2 dos 3 outros (t = 2)
        assertThat(p.get(3L)).isEqualTo(BAIXO); // desce, mas baixo não desce
        assertThat(p.get(4L)).isEqualTo(MEDIO); // zero conta: alto desce para médio
        var topo = PesoBanca.propor(List.of(new Linha(1, ALTO, 9), new Linha(2, MEDIO, 1), new Linha(3, MEDIO, 0)));
        assertThat(topo.get(1L)).isEqualTo(ALTO); // alto não sobe
    }

    @Test
    void d146_empate_naoSeparaQuemTemOMesmoNumero() {
        var p = PesoBanca.propor(List.of(new Linha(1, MEDIO, 10), new Linha(2, MEDIO, 10), new Linha(3, MEDIO, 0)));
        assertThat(p.get(1L)).isEqualTo(MEDIO);
        assertThat(p.get(2L)).isEqualTo(MEDIO);
        assertThat(p.get(3L)).isEqualTo(BAIXO);
        var iguais = PesoBanca.propor(List.of(new Linha(1, MEDIO, 0), new Linha(2, BAIXO, 0)));
        assertThat(iguais.get(1L)).isEqualTo(MEDIO);
        assertThat(iguais.get(2L)).isEqualTo(BAIXO);
        assertThat(PesoBanca.propor(List.of(new Linha(1, MEDIO, 50))).get(1L)).isEqualTo(MEDIO); // n = 1
    }
}
