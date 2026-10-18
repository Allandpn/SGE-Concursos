package br.com.estudos.concurso;

import io.swagger.v3.oas.annotations.media.Schema;

import br.com.estudos.segmento.MaterialService;

/** Uma linha de "Ver por assunto" (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §5A). */
public record MaterialAssuntoResponse(
    Long assuntoId,
    String disciplina,
    String assunto,
    @Schema(description = "PRONTO, SEM_MATERIAL ou A_PREPARAR (derivado, D-16)") MaterialService.Situacao situacao,
    @Schema(description = "Segmentos ativos") int segmentos,
    @Schema(description = "Soma do tempo estimado dos segmentos ativos, em minutos") long minutos,
    @Schema(description = "Soma das páginas dos segmentos ativos que informam início e fim") long paginas,
    @Schema(description = "Motivo da lacuna (D-65); só em SEM_MATERIAL", nullable = true) String motivo
) {}
