package br.com.estudos.metrica;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** M-2 — retenção por tipo de sessão (00_PRODUTO §7). */
public record M2Response(
    @Schema(description = "Assunto consultado") Long assuntoId,
    @Schema(description = "Uma série por tipo de sessão, nunca somadas entre si") List<M2SerieResponse> series
) {}
