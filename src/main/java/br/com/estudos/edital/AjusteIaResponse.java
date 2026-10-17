package br.com.estudos.edital;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** Ajustes → IA, como a tela vê — nunca token nem chave (ADR-044, ADR-045). */
public record AjusteIaResponse(
    @Schema(description = "Provedor em uso; só o usuário troca") ProvedorIa provedor,
    @Schema(description = "Se há credencial para o provedor em uso") boolean disponivel,
    Claude claude,
    OpenRouter openrouter
) {
    public record Claude(
        @Schema(description = "Se há token da assinatura cadastrado") boolean configurado,
        @Schema(description = "Quatro últimos caracteres do token; nulo sem token") String tokenFinal,
        @Schema(description = "opus, sonnet ou haiku") String modelo,
        @Schema(description = "Última falha da assinatura — o aviso das telas; nulo quando está respondendo") Falha falha
    ) {}

    public record Falha(String motivo, Instant em) {}

    public record OpenRouter(
        @Schema(description = "Se há chave do OpenRouter") boolean configurado,
        @Schema(description = "Quatro últimos caracteres da chave; nulo sem chave") String chaveFinal,
        @Schema(description = "Modelo do OpenRouter") String modelo,
        @Schema(description = "De onde vem a chave") ConfiguracaoIa.Origem origem
    ) {}
}
