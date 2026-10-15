package br.com.estudos.edital;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.estudos.edital.PesoPorQuestoes.PesoCalculado;
import br.com.estudos.shared.enums.TipoPeso;

/** D-61 — peso por tercis do número de questões, nunca estimado. */
class PesoPorQuestoesTest {

    @Test
    void d61_tercosDaMaiorParaAMenor() {
        assertEquals(List.of(alto(), alto(), medio(), medio(), baixo(), baixo()),
            PesoPorQuestoes.calcular(List.of(30, 25, 20, 15, 10, 5)));
    }

    @Test
    void d61_empateRecebeOMesmoPeso() {
        assertEquals(List.of(alto(), alto(), baixo()), PesoPorQuestoes.calcular(List.of(10, 10, 5)));
    }

    @Test
    void d61_semContagemFicaMedioNaoDeterminado() {
        var naoDeterminado = new PesoCalculado(TipoPeso.MEDIO, false);
        assertEquals(List.of(alto(), naoDeterminado, medio()),
            PesoPorQuestoes.calcular(Arrays.asList(20, null, 10)));
        assertEquals(List.of(naoDeterminado, naoDeterminado),
            PesoPorQuestoes.calcular(Arrays.asList(null, 0)));
    }

    private static PesoCalculado alto() { return new PesoCalculado(TipoPeso.ALTO, true); }
    private static PesoCalculado medio() { return new PesoCalculado(TipoPeso.MEDIO, true); }
    private static PesoCalculado baixo() { return new PesoCalculado(TipoPeso.BAIXO, true); }
}
