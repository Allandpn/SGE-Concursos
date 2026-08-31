package br.com.estudos.simulado;

import java.util.List;

/** Entidade nunca sai do Service (09_CODE_STYLE §9) — o Controller devolve isto. */
public final class SimuladoMapper {

    private SimuladoMapper() {}

    public static SimuladoResponse toResponse(Simulado simulado, List<ResultadoSimulado> resultados) {
        return new SimuladoResponse(
            simulado.getId(),
            simulado.getData(),
            simulado.getDuracaoMinutos(),
            resultados.stream().map(SimuladoMapper::toResultadoResponse).toList()
        );
    }

    private static ResultadoSimuladoResponse toResultadoResponse(ResultadoSimulado resultado) {
        return new ResultadoSimuladoResponse(
            resultado.getDisciplina().getId(),
            resultado.getFormato(),
            resultado.getQuestoesCorretas(),
            resultado.getQuestoesTotal()
        );
    }
}
