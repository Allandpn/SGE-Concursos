package br.com.estudos.metrica;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** M-1 — aderência ao objetivo (00_PRODUTO §7). */
public record M1Response(
    @Schema(description = "Janela de tempo considerada no cálculo") JanelaMetrica janela,
    @Schema(description = "Uma linha por disciplina, mais a linha GLOBAL (disciplinaId nulo)") List<M1LinhaResponse> linhas
) {}
