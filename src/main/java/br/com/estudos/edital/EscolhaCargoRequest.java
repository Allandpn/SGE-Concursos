package br.com.estudos.edital;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cargo escolhido entre os que a etapa 1 encontrou. */
public record EscolhaCargoRequest(
    @Schema(description = "Um dos cargos da análise, exatamente como listado") String cargo
) {}
