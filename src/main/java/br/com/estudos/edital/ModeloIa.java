package br.com.estudos.edital;

import io.swagger.v3.oas.annotations.media.Schema;

/** Um modelo do OpenRouter, para a escolha em Ajustes → IA (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3). */
public record ModeloIa(
    @Schema(description = "Identificador no OpenRouter", example = "anthropic/claude-sonnet-5.5") String id,
    @Schema(description = "Nome de exibição") String nome,
    @Schema(description = "Preço por milhão de tokens de entrada, em dólares; nulo se o OpenRouter não informa") Double precoEntradaPorMilhao,
    @Schema(description = "Preço por milhão de tokens de saída, em dólares; nulo se o OpenRouter não informa") Double precoSaidaPorMilhao
) {}
