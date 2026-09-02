package br.com.estudos.simulado;

import br.com.estudos.shared.enums.FormatoBanca;
import io.swagger.v3.oas.annotations.media.Schema;

/** Um item de resultado por disciplina, dentro de {@link SimuladoRequest}. */
public record ResultadoSimuladoRequest(
    @Schema(description = "Disciplina deste resultado", example = "1")
    Long disciplinaId,

    @Schema(description = "Formato da banca — limiares e M-1 são por formato, nunca somados (D-36)")
    FormatoBanca formato,

    @Schema(description = "Questões corretas nesta disciplina", example = "32")
    Integer questoesCorretas,

    @Schema(description = "Total de questões nesta disciplina", example = "40")
    Integer questoesTotal
) {}
