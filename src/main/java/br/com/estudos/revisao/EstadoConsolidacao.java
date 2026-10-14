package br.com.estudos.revisao;

import java.util.List;

import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * D-10/D-11 (01_DOMINIO §5.5, v1.19.0) — se o assunto está consolidado, como
 * sequência dos resultados cumpridos no nível-alvo. Função pura: o único lugar
 * da regra, usado pelo roteamento (RevisaoService) e pela fase derivada
 * (FrenteService) — ADR-033. docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §1.1.
 */
public final class EstadoConsolidacao {

    private EstadoConsolidacao() {}

    /** D-23 (01_DOMINIO §6.7) — sem coluna própria, mapeamento fixo. */
    public static int nivelAlvo(TipoPeso peso) {
        return switch (peso) {
            case ALTO -> 6;
            case MEDIO -> 4;
            case BAIXO -> 3;
        };
    }

    /**
     * Antes de consolidar, dois SUCESSO seguidos consolidam e qualquer outro
     * resultado zera a sequência. Depois de consolidado, SUCESSO e PARCIAL
     * mantêm; só FALHA desfaz (D-11).
     *
     * @param resultadosNoAlvo resultados das revisões cumpridas no nível-alvo, da mais antiga à mais nova
     */
    public static boolean consolidado(List<ResultadoSessao> resultadosNoAlvo) {
        var consolidado = false;
        var seguidos = 0;
        for (var resultado : resultadosNoAlvo) {
            if (consolidado) {
                if (resultado == ResultadoSessao.FALHA) {
                    consolidado = false;
                    seguidos = 0;
                }
            } else if (resultado == ResultadoSessao.SUCESSO) {
                seguidos++;
                consolidado = seguidos >= 2;
            } else {
                seguidos = 0;
            }
        }
        return consolidado;
    }
}
