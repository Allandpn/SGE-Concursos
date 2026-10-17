package br.com.estudos.edital;

import io.swagger.v3.oas.annotations.media.Schema;

/** Ajustes → IA, como a tela vê — nunca a chave (ADR-044). */
public record AjusteIaResponse(
    @Schema(description = "Se há chave cadastrada ou no ambiente") boolean disponivel,
    @Schema(description = "Modelo em uso") String modelo,
    @Schema(description = "Quatro últimos caracteres da chave em uso; nulo sem chave", example = "a1b2") String chaveFinal,
    @Schema(description = "De onde vem a chave em uso") ConfiguracaoIa.Origem origem
) {}
