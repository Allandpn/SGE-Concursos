package br.com.estudos.turno;

import io.swagger.v3.oas.annotations.media.Schema;

/** O segmento que o bloco de conteúdo do turno sugere ler (D-55, docs/SPRINT-17-USO-REAL.md §1.2). */
public record SegmentoSugeridoResponse(
    @Schema(description = "Identificador do segmento") Long id,
    @Schema(description = "Posição do segmento no assunto") Integer ordem,
    @Schema(description = "Quantos segmentos o assunto tem") int total,
    @Schema(description = "Link/identificador do material — o SGE guarda, nunca abre") String arquivo,
    @Schema(description = "Página inicial do material de origem") Integer paginaInicial,
    @Schema(description = "Página final do material de origem") Integer paginaFinal,
    @Schema(description = "Estimativa de leitura em minutos") Integer tempoEstimadoMin
) {}
