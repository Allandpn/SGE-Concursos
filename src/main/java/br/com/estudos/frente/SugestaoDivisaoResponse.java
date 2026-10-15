package br.com.estudos.frente;

import io.swagger.v3.oas.annotations.media.Schema;

/** Um assunto que o balanço semanal sugere dividir (D-35, docs/SPRINT-17-USO-REAL.md §6.1). Só sugestão: nada é dividido. */
public record SugestaoDivisaoResponse(
    @Schema(description = "Assunto com parciais seguidos") Long assuntoId,
    @Schema(description = "Nome do assunto") String nome,
    @Schema(description = "Disciplina do assunto") Long disciplinaId,
    @Schema(description = "Nome da disciplina") String disciplina,
    @Schema(description = "Quantos resultados PARCIAL seguidos, a partir do mais recente") int parciaisSeguidos
) {}
