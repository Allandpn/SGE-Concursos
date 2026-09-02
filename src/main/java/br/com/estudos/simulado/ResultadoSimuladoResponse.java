package br.com.estudos.simulado;

import br.com.estudos.shared.enums.FormatoBanca;
import io.swagger.v3.oas.annotations.media.Schema;

/** Um item de resultado por disciplina, dentro de {@link SimuladoResponse}. */
public record ResultadoSimuladoResponse(
    @Schema(description = "Disciplina deste resultado") Long disciplinaId,
    @Schema(description = "Formato da banca") FormatoBanca formato,
    @Schema(description = "Questões corretas nesta disciplina") int questoesCorretas,
    @Schema(description = "Total de questões nesta disciplina") int questoesTotal
) {}
