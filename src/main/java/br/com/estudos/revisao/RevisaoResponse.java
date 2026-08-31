package br.com.estudos.revisao;

import java.time.LocalDate;

import br.com.estudos.shared.enums.SituacaoRevisao;

/** Corpo de resposta de revisão (docs/SPRINT-4-ESCADA.md §4). */
public record RevisaoResponse(
    Long id,
    Long assuntoId,
    Integer nivel,
    LocalDate dataPrevista,
    Long sessaoOrigemId,
    Long sessaoCumpriuId,
    SituacaoRevisao situacao
) {}
