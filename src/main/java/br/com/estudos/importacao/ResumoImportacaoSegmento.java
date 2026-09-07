package br.com.estudos.importacao;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado de validar/confirmar segmentos (docs/SPRINT-10-SEGMENTO.md §4). */
public record ResumoImportacaoSegmento(
    @Schema(description = "Quantos segmentos novos o arquivo criaria/criou") int segmentosNovos,
    @Schema(description = "Quantos segmentos existentes o arquivo atualizaria/atualizou (upsert por chaveExterna)") int segmentosAtualizados,
    @Schema(description = "Linhas recusadas e o motivo — se não vazio em /validar, /confirmar recusa o arquivo inteiro (regra 1)") List<LinhaRecusada> recusadas
) {}
