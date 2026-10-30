package br.com.estudos.foco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import br.com.estudos.foco.FaseFoco.Especie;
import br.com.estudos.foco.FaseFoco.Motivo;

/** D-99 e D-103: o tempo de uma fase é função do início gravado, com instantes fixos (nada de relógio de parede). */
class FocoCalculoTest {

    static final Instant T0 = Instant.parse("2026-10-30T12:00:00Z");

    private static FaseFoco foco(Instant pausadoDesde, int pausadoAcumuladoS, Instant fim, Motivo motivo) {
        return new FaseFoco(1L, null, Especie.FOCO, null, null, T0, 25 * 60, pausadoDesde, pausadoAcumuladoS, fim, motivo, null, null);
    }

    @Test
    void d99_focoEmCurso_contaOQueCorreu() {
        var f = foco(null, 0, null, null);
        assertEquals(600, FocoCalculo.decorridoS(f, T0.plusSeconds(600)));
        assertEquals(600, FocoCalculo.focoCorridoS(f, T0.plusSeconds(600)));
        assertEquals(900, FocoCalculo.restanteS(f, T0.plusSeconds(600)));
        assertFalse(FocoCalculo.venceu(f, T0.plusSeconds(600)));
    }

    @Test
    void d99_pausaManualCongelaORelogio() {
        var pausadaAos10min = foco(T0.plusSeconds(600), 0, null, null);
        assertEquals(600, FocoCalculo.decorridoS(pausadaAos10min, T0.plusSeconds(600 + 7200)));
        assertFalse(FocoCalculo.venceu(pausadaAos10min, T0.plusSeconds(600 + 7200)), "pausada nunca vence");
    }

    @Test
    void d99_pausaJaRetomadaSaiDoTempo() {
        var f = foco(null, 300, null, null);
        assertEquals(1200, FocoCalculo.decorridoS(f, T0.plusSeconds(1500)));
        assertEquals(T0.plusSeconds(25 * 60 + 300), FocoCalculo.vencimento(f));
        assertTrue(FocoCalculo.venceu(f, T0.plusSeconds(25 * 60 + 300)));
    }

    @Test
    void d99_focoVencidoSemEncerramento_contaSoADuracaoProgramada() {
        var vencida = foco(null, 0, T0.plusSeconds(25 * 60), Motivo.VENCIDA);
        assertEquals(25 * 60, FocoCalculo.focoCorridoS(vencida, T0.plusSeconds(7200)));
        // mesmo que alguém consulte uma fase em curso duas horas depois, nunca passa da duração
        assertEquals(25 * 60, FocoCalculo.focoCorridoS(foco(null, 0, null, null), T0.plusSeconds(7200)));
    }

    @Test
    void d99_focoEncerradoAntesDoFim_contaOCorrido() {
        var f = foco(null, 120, T0.plusSeconds(800), Motivo.ENCERRADA);
        assertEquals(680, FocoCalculo.focoCorridoS(f, T0.plusSeconds(9999)));
    }

    @Test
    void d99_pausaCurtaELongaNuncaContam() {
        for (var especie : new Especie[] { Especie.PAUSA_CURTA, Especie.PAUSA_LONGA }) {
            var p = new FaseFoco(2L, null, especie, null, null, T0, 300, null, 0, T0.plusSeconds(300), Motivo.VENCIDA, null, null);
            assertEquals(0, FocoCalculo.focoCorridoS(p, T0.plusSeconds(300)));
        }
    }

    @Test
    void d100_minutosArredondaEQualquerFocoRealVale1() {
        assertEquals(0, FocoCalculo.minutos(0));
        assertEquals(1, FocoCalculo.minutos(20));
        assertEquals(2, FocoCalculo.minutos(90));
        assertEquals(50, FocoCalculo.minutos(50 * 60));
    }
}
