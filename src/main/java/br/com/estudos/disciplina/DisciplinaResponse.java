package br.com.estudos.disciplina;

import br.com.estudos.shared.enums.TipoPeso;
import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de resposta de disciplina (docs/SPRINT-2-CADASTRO.md §4). */
public record DisciplinaResponse(
    @Schema(description = "Identificador da disciplina") Long id,
    @Schema(description = "Nome da disciplina", example = "Banco de Dados") String nome,
    @Schema(description = "Peso da disciplina no edital") TipoPeso peso,
    @Schema(description = "false quando arquivada (D-18: nunca removida, só arquivada)") boolean ativo
) {}
