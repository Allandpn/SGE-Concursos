package br.com.estudos.simulado;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de POST de simulado (docs/SPRINT-8-SIMULADO.md §5). Tudo-ou-nada (§3). */
public record SimuladoRequest(
    @Schema(description = "Data em que o simulado foi feito", example = "2026-02-01")
    LocalDate data,

    @Schema(description = "Duração total do simulado em minutos", example = "240")
    Integer duracaoMinutos,

    @Schema(description = "Resultado por disciplina — uma disciplina não pode se repetir no mesmo simulado (D-13)")
    List<ResultadoSimuladoRequest> resultados
) {}
