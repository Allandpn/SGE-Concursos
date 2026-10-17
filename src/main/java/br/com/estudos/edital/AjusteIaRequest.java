package br.com.estudos.edital;

import io.swagger.v3.oas.annotations.media.Schema;

/** Ajustes → IA. Campo ausente ou em branco mantém o atual (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3A). */
public record AjusteIaRequest(
    @Schema(description = "Provedor a usar; escolher CLAUDE apaga a falha registrada") ProvedorIa provedor,
    @Schema(description = "Token da assinatura (claude setup-token); nunca é devolvido") String claudeToken,
    @Schema(description = "opus, sonnet ou haiku") String claudeModelo,
    @Schema(description = "Chave do OpenRouter; nunca é devolvida") String chave,
    @Schema(description = "Modelo do OpenRouter", example = "anthropic/claude-sonnet-5.5") String modelo
) {}
