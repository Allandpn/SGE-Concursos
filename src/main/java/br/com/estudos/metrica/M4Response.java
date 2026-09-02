package br.com.estudos.metrica;

import io.swagger.v3.oas.annotations.media.Schema;

/** `percentual` nulo quando ainda não há nenhuma revisão CUMPRIDA. */
public record M4Response(
    @Schema(description = "Percentual de revisões cumpridas dentro dos três dias — null sem nenhuma CUMPRIDA ainda") Double percentual,
    @Schema(description = "Quantidade cumprida dentro do prazo") int dentroDoPrazo,
    @Schema(description = "Total de revisões cumpridas") int totalCumpridas
) {}
