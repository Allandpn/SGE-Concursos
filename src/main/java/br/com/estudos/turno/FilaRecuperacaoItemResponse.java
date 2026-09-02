package br.com.estudos.turno;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** Um item da fila de recuperação do turno (docs/SPRINT-6-TURNO.md §2). */
public record FilaRecuperacaoItemResponse(
    @Schema(description = "Assunto a recuperar") Long assuntoId,
    @Schema(description = "Nome do assunto") String nome,
    @Schema(description = "Nível da escada em que está") Integer nivel,
    @Schema(description = "Data em que a revisão estava prevista") LocalDate dataPrevista,
    @Schema(description = "Dias de atraso — 0 ou negativo se ainda não venceu") long diasAtraso
) {}
