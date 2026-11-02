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
    Integer questoesTotal,

    @Schema(description = "Questões em branco nesta disciplina — opcional; certas + brancos ≤ total (D-148)", example = "3")
    Integer questoesEmBranco
) {
    /** Sem brancos: o formato de antes da Sprint 34 (a prova hospedada e os testes antigos). */
    public ResultadoSimuladoRequest(Long disciplinaId, FormatoBanca formato, Integer questoesCorretas, Integer questoesTotal) {
        this(disciplinaId, formato, questoesCorretas, questoesTotal, null);
    }
}
