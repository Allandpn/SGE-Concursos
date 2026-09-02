package br.com.estudos.metrica;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Contagem, não média com sinal como a objetiva — mesmo agora que os dois
 * lados (`previsaoReconstrucao`/`resultado`) têm a mesma escala de três
 * vias, ninguém pediu essa mudança de forma (docs/SPRINT-7-METRICAS.md
 * §4.3).
 */
public record M3SubjetivaResponse(
    @Schema(description = "Previu melhor do que aconteceu") int superestimou,
    @Schema(description = "Previu pior do que aconteceu") int subestimou,
    @Schema(description = "Previsão bateu com o resultado") int acertouPrevisao,
    @Schema(description = "Quantidade de observações") int n
) {}
