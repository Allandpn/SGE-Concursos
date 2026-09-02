package br.com.estudos.revisao;

import java.time.LocalDate;

import br.com.estudos.shared.enums.SituacaoRevisao;
import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de resposta de revisão (docs/SPRINT-4-ESCADA.md §4). */
public record RevisaoResponse(
    @Schema(description = "Identificador da revisão") Long id,
    @Schema(description = "Assunto ao qual a revisão pertence") Long assuntoId,
    @Schema(description = "Nível da escada (D-08 — intervalo fixo por nível)", example = "1") Integer nivel,
    @Schema(description = "Data em que a revisão está prevista") LocalDate dataPrevista,
    @Schema(description = "Sessão que originou esta revisão") Long sessaoOrigemId,
    @Schema(description = "Sessão que cumpriu esta revisão — preenchida só quando situacao é CUMPRIDA") Long sessaoCumpriuId,
    @Schema(description = "Pendente, cumprida ou cancelada") SituacaoRevisao situacao
) {}
