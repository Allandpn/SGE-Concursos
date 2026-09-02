package br.com.estudos.frente;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resumo da frente de estudo (docs/SPRINT-5-FRENTE.md §2.2), tudo derivado, nada persistido (ADR-033). */
public record FrenteResumoResponse(
    @Schema(description = "Quantos assuntos estão na frente agora (em estudo ou em escada)") int frenteAtual,
    @Schema(description = "Teto global de assuntos na frente ao mesmo tempo") int tetoGlobalFrente,
    @Schema(description = "Quantos assuntos estão no backlog, ainda não iniciados") int backlog,
    @Schema(description = "Quantos assuntos já consolidaram a escada") int consolidados,
    @Schema(description = "Quantas revisões pendentes já venceram (represamento)") long represado,
    @Schema(description = "Teto diário de recuperações oferecidas") int tetoDiario,
    @Schema(description = "Se o represamento passou do limiar de alerta") boolean alertaRepresamento,
    @Schema(description = "Estimativa de dias para zerar o represado, no teto diário atual") long estimativaDiasParaNormalizar,
    @Schema(description = "D-31 — se o teto diário não sustenta a frente nem no melhor caso") boolean avisoTetoInsuficiente
) {}
