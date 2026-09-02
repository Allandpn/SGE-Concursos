package br.com.estudos.assunto;

import br.com.estudos.shared.enums.TipoPeso;
import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de resposta de assunto (docs/SPRINT-2-CADASTRO.md §4). */
public record AssuntoResponse(
    @Schema(description = "Identificador do assunto") Long id,
    @Schema(description = "Disciplina à qual o assunto pertence") Long disciplinaId,
    @Schema(description = "Nome do assunto", example = "Normalização e Dependências Funcionais") String nome,
    @Schema(description = "Peso do assunto no edital") TipoPeso peso,
    @Schema(description = "Dificuldade percebida, de 1 a 5") Short dificuldadePercebida,
    @Schema(description = "Prioridade no backlog da disciplina") Integer ordem,
    @Schema(description = "false quando arquivado (D-18: nunca removido, só arquivado)") boolean ativo
) {}
