package br.com.estudos.metrica;

import io.swagger.v3.oas.annotations.media.Schema;

/** `mediaDesvio` = previsto − real, com sinal (positivo: superestimou). */
public record M3ObjetivaResponse(
    @Schema(description = "Média do desvio previsto menos real, com sinal — positivo é superestimou") double mediaDesvio,
    @Schema(description = "Quantidade de observações") int n
) {}
