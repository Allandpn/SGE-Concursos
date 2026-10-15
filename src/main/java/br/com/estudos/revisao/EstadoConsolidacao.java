package br.com.estudos.revisao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
        // Mesma regra de consolidadoDesde, sem datas: uma implementação só.
        return consolidadoDesde(resultadosNoAlvo.stream()
            .map(resultado -> new ResultadoNaData(resultado, LocalDate.MIN))
            .toList()).isPresent();
    }

    /** Um resultado cumprido no nível-alvo e a data da sessão que o produziu. */
    public record ResultadoNaData(ResultadoSessao resultado, LocalDate data) {}

    /**
     * Mesma sequência de {@link #consolidado}, devolvendo desde quando o
     * assunto está consolidado — a data do resultado que fechou os dois
     * SUCESSO seguidos da consolidação vigente. Vazio se não está consolidado.
     * Usado pelo balanço semanal (02_JORNADAS J-3, docs/SPRINT-17-USO-REAL.md §6.2).
     */
    public static Optional<LocalDate> consolidadoDesde(List<ResultadoNaData> resultadosNoAlvo) {
        var consolidado = false;
        var seguidos = 0;
        LocalDate desde = null;
        for (var item : resultadosNoAlvo) {
            var resultado = item.resultado();
            if (consolidado) {
                if (resultado == ResultadoSessao.FALHA) {
                    consolidado = false;
                    seguidos = 0;
                    desde = null;
                }
            } else if (resultado == ResultadoSessao.SUCESSO) {
                seguidos++;
                if (seguidos >= 2) {
                    consolidado = true;
                    desde = item.data();
                }
            } else {
                seguidos = 0;
            }
        }
        return Optional.ofNullable(desde);
    }
}
