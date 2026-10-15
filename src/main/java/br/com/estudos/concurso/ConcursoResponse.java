package br.com.estudos.concurso;

import java.time.Instant;
import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

import br.com.estudos.shared.enums.OrigemConcurso;

/** Concurso-alvo ou do histórico (docs/SPRINT-18-CONCURSOS.md §2). */
public record ConcursoResponse(
    @Schema(description = "Identificador do concurso") Long id,
    @Schema(description = "Nome do concurso", example = "SEFAZ SC 2026") String nome,
    @Schema(description = "Cargo escolhido na análise do edital; nulo na importação do planejamento") String cargo,
    @Schema(description = "Banca organizadora, quando conhecida") String banca,
    @Schema(description = "Data da prova, quando o edital já a fixou") LocalDate dataProva,
    @Schema(description = "Lido por IA no SGE ou importado do planejamento") OrigemConcurso origem,
    @Schema(description = "true no concurso-alvo (D-58: no máximo um)") boolean ativo,
    @Schema(description = "Início do período em que foi alvo") Instant ativadoEm,
    @Schema(description = "Fim do período; nulo no ativo (D-59)") Instant desativadoEm,
    @Schema(description = "Dias até a prova pela data do servidor; nulo sem data; negativo depois dela") Long diasParaProva,
    @Schema(description = "Quantos assuntos o edital cobre") int totalAssuntos,
    @Schema(description = "Quantos destes assuntos também estão no edital do concurso ativo") long emComumComAtivo
) {}
