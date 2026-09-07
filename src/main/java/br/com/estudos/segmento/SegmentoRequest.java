package br.com.estudos.segmento;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo de criarOuAtualizar (docs/SPRINT-10-SEGMENTO.md §3.2) — usado só
 * internamente por {@code ImportacaoSegmentoService}, sem endpoint de escrita
 * livre pela API (Segmento nasce só por importação, 01_DOMINIO §3.7).
 */
public record SegmentoRequest(
    @Schema(description = "Assunto ao qual o segmento pertence") Long assuntoId,

    @Schema(description = "Identificador do segmento dado pelo sistema de planejamento — único entre todos os segmentos (D-53), identidade de reimportação")
    String chaveExterna,

    @Schema(description = "Posição do segmento dentro do assunto — única por assunto (D-50), mas não é identidade") Integer ordem,

    @Schema(description = "Link/identificador do material — guardado, nunca aberto pelo SGE") String arquivo,

    @Schema(description = "Página inicial do material de origem, para rastreabilidade") Integer paginaInicial,

    @Schema(description = "Página final do material de origem, para rastreabilidade") Integer paginaFinal,

    @Schema(description = "Estimativa de leitura em minutos, informativa") Integer tempoEstimadoMin
) {}
