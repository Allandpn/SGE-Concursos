package br.com.estudos.segmento;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de resposta de segmento (docs/SPRINT-10-SEGMENTO.md §5). */
public record SegmentoResponse(
    @Schema(description = "Identificador do segmento") Long id,
    @Schema(description = "Assunto ao qual o segmento pertence") Long assuntoId,
    @Schema(description = "Identificador dado pelo sistema de planejamento (D-53)") String chaveExterna,
    @Schema(description = "Posição do segmento dentro do assunto") Integer ordem,
    @Schema(description = "Link/identificador do material — o SGE guarda, nunca abre") String arquivo,
    @Schema(description = "Página inicial do material de origem") Integer paginaInicial,
    @Schema(description = "Página final do material de origem") Integer paginaFinal,
    @Schema(description = "Estimativa de leitura em minutos") Integer tempoEstimadoMin
) {}
