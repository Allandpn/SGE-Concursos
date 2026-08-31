package br.com.estudos.turno;

import java.time.LocalDate;

/** Um item da fila de recuperação do turno (docs/SPRINT-6-TURNO.md §2). */
public record FilaRecuperacaoItemResponse(
    Long assuntoId,
    String nome,
    Integer nivel,
    LocalDate dataPrevista,
    long diasAtraso
) {}
