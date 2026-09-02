package br.com.estudos.simulado;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de resposta de simulado (docs/SPRINT-8-SIMULADO.md §5). */
public record SimuladoResponse(
    @Schema(description = "Identificador do simulado") Long id,
    @Schema(description = "Data em que o simulado foi feito") LocalDate data,
    @Schema(description = "Duração total em minutos") Integer duracaoMinutos,
    @Schema(description = "Resultado por disciplina") List<ResultadoSimuladoResponse> resultados
) {}
