package br.com.estudos.revisao;

import static br.com.estudos.shared.enums.ResultadoSessao.FALHA;
import static br.com.estudos.shared.enums.ResultadoSessao.PARCIAL;
import static br.com.estudos.shared.enums.ResultadoSessao.SUCESSO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * D-10/D-11 como sequência no nível-alvo (01_DOMINIO §5.5 v1.19.0,
 * docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §1.1) — a tabela linha a linha.
 * Unitário: função pura, sem Spring nem banco.
 */
class EstadoConsolidacaoTest {

    @Test
    void semResultado_naoConsolidado() {
        assertFalse(EstadoConsolidacao.consolidado(List.of()));
    }

    @Test
    void umSucesso_naoConsolida() {
        assertFalse(EstadoConsolidacao.consolidado(List.of(SUCESSO)));
    }

    @Test
    void doisSucessosSeguidos_consolidam() {
        assertTrue(EstadoConsolidacao.consolidado(List.of(SUCESSO, SUCESSO)));
    }

    @Test
    void parcialAntesDeConsolidar_zeraASequencia() {
        assertFalse(EstadoConsolidacao.consolidado(List.of(SUCESSO, PARCIAL, SUCESSO)));
    }

    @Test
    void falhaAntesDeConsolidar_zeraASequencia() {
        assertFalse(EstadoConsolidacao.consolidado(List.of(SUCESSO, FALHA, SUCESSO)));
    }

    @Test
    void parcialDepoisDeConsolidado_mantem() {
        assertTrue(EstadoConsolidacao.consolidado(List.of(SUCESSO, SUCESSO, PARCIAL)));
        assertTrue(EstadoConsolidacao.consolidado(List.of(SUCESSO, SUCESSO, PARCIAL, PARCIAL, SUCESSO)));
    }

    @Test
    void falhaDepoisDeConsolidado_desfaz_D11() {
        assertFalse(EstadoConsolidacao.consolidado(List.of(SUCESSO, SUCESSO, FALHA)));
    }

    @Test
    void reconsolidarDepoisDeFalha_exigeDoisSucessosSeguidos() {
        assertFalse(EstadoConsolidacao.consolidado(List.of(SUCESSO, SUCESSO, FALHA, SUCESSO)));
        assertTrue(EstadoConsolidacao.consolidado(List.of(SUCESSO, SUCESSO, FALHA, SUCESSO, SUCESSO)));
    }

    private static EstadoConsolidacao.ResultadoNaData em(br.com.estudos.shared.enums.ResultadoSessao r, int dia) {
        return new EstadoConsolidacao.ResultadoNaData(r, java.time.LocalDate.of(2026, 1, dia));
    }

    @Test
    void consolidadoDesde_dataDoSegundoSucesso() {
        assertEquals(java.util.Optional.of(java.time.LocalDate.of(2026, 1, 9)),
            EstadoConsolidacao.consolidadoDesde(List.of(em(SUCESSO, 2), em(SUCESSO, 9), em(PARCIAL, 20))),
            "PARCIAL depois não muda a data da consolidação vigente");
    }

    @Test
    void consolidadoDesde_reconsolidacaoTrocaAData() {
        assertEquals(java.util.Optional.of(java.time.LocalDate.of(2026, 1, 28)),
            EstadoConsolidacao.consolidadoDesde(List.of(em(SUCESSO, 2), em(SUCESSO, 9), em(FALHA, 15), em(SUCESSO, 21), em(SUCESSO, 28))));
    }

    @Test
    void consolidadoDesde_vazioSemConsolidacao() {
        assertTrue(EstadoConsolidacao.consolidadoDesde(List.of(em(SUCESSO, 2), em(PARCIAL, 9))).isEmpty());
    }
}
