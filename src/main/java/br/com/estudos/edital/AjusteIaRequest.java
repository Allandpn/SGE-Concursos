package br.com.estudos.edital;

import io.swagger.v3.oas.annotations.media.Schema;

/** Ajustes → IA. Campo ausente ou em branco mantém o atual. */
public record AjusteIaRequest(
    @Schema(description = "Chave do OpenRouter; nunca é devolvida") String chave,
    @Schema(description = "Modelo do OpenRouter", example = "anthropic/claude-sonnet-5.5") String modelo
) {}
