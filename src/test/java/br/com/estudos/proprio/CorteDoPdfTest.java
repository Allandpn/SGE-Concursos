package br.com.estudos.proprio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import br.com.estudos.proprio.CorteDoPdf.Bloco;

/** D-129 (docs/SPRINT-33-PDF-PROPRIO.md §4): o corte por ritmo, pelos marcadores e a sobra pequena. */
class CorteDoPdfTest {

    @Test
    void d129_apostilaCortaATrintaPaginas_semMarcadores() {
        assertThat(CorteDoPdf.cortar(70, 2, new TreeSet<>())).containsExactly(new Bloco(1, 30), new Bloco(31, 60), new Bloco(61, 70));
    }

    @Test
    void d129_leiSecaCortaAVintePaginas() {
        assertThat(CorteDoPdf.cortar(40, 3, new TreeSet<>())).containsExactly(new Bloco(1, 20), new Bloco(21, 40));
    }

    @Test
    void d129_marcadorDentroDaFaixa_puxaOCorte() {
        // Alvo 30; faixa de fim 23..37 (início de seção 24..38). Seção começando na 27 → bloco termina na 26.
        assertThat(CorteDoPdf.cortar(60, 2, new TreeSet<>(java.util.List.of(1, 9, 27, 50)))).first().isEqualTo(new Bloco(1, 26));
    }

    @Test
    void d129_marcadorForaDaFaixa_naoPuxa() {
        assertThat(CorteDoPdf.cortar(60, 2, new TreeSet<>(java.util.List.of(10, 45)))).first().isEqualTo(new Bloco(1, 30));
    }

    @Test
    void d129_sobraPequenaJuntaAoUltimo() {
        assertThat(CorteDoPdf.cortar(33, 2, new TreeSet<>())).containsExactly(new Bloco(1, 33));
        assertThat(CorteDoPdf.cortar(5, 2, new TreeSet<>())).containsExactly(new Bloco(1, 5));
    }
}
