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
    @Schema(description = "Motivo da lacuna (D-65); só em SEM_MATERIAL", nullable = true) String motivo,
    @Schema(description = "Situação do casamento por partes: COMPLETO, PARCIAL, SEM_MATERIAL ou RECUSADO; nulo se o assunto não nasceu de uma conferência ou ainda está a preparar", nullable = true) String situacaoDoCasamento,
    @Schema(description = "Tempo estimado de leitura que o planejamento calculou, em minutos", nullable = true) Integer minutosEstimados,
    @Schema(description = "As partes do assunto que nenhum tema do acervo ensina, com o motivo e o destino que a pessoa deu (POR_FORA, IGNORADA ou nulo)") java.util.List<br.com.estudos.conferencia.MaterialDaConferencia.Lacuna> lacunasPorParte,
    @Schema(description = "O PDF \"Questões deste assunto\" (consulta, fora da escada, D-94), servido em /material/", nullable = true) String consulta,
    @Schema(description = "Páginas do PDF de consulta", nullable = true) Integer consultaPaginas
) {}
