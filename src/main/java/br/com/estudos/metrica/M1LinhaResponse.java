package br.com.estudos.metrica;

import br.com.estudos.shared.enums.FormatoBanca;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * `disciplinaId` vem nulo na janela GLOBAL (soma entre disciplinas).
 * `percentual` nulo abaixo de n=10 (00_PRODUTO §7 — nunca `0%`).
 * `confiavel` marca n≥100: só aí a tela pode mostrar seta/cor/comparação.
 */
public record M1LinhaResponse(
    @Schema(description = "Disciplina desta linha — null na linha GLOBAL") Long disciplinaId,
    @Schema(description = "Formato da banca — nunca somado com outro formato (D-36)") FormatoBanca formato,
    @Schema(description = "Questões corretas na janela") int acertos,
    @Schema(description = "Total de questões na janela") int total,
    @Schema(description = "true só com n≥100 — abaixo disso a tela não deve comparar/colorir") boolean confiavel,
    @Schema(description = "Percentual de acerto — null se n<10, nunca 0%") Double percentual
) {}
