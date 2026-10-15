package br.com.estudos.edital;

import io.swagger.v3.oas.annotations.media.Schema;

/** Se a análise por IA pode ser usada agora (ADR-043). */
public record IaStatusResponse(
    @Schema(description = "false sem chave do OpenRouter configurada") boolean disponivel,
    @Schema(description = "Por que não está disponível, para mostrar como veio") String motivo,
    @Schema(description = "Modelo configurado") String modelo
) {}
