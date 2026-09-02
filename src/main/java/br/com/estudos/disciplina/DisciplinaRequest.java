package br.com.estudos.disciplina;

import br.com.estudos.shared.enums.TipoPeso;
import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de POST/PATCH de disciplina (docs/SPRINT-2-CADASTRO.md §4). */
public record DisciplinaRequest(
    @Schema(description = "Nome da disciplina, único mesmo arquivada (D-47)", example = "Banco de Dados")
    String nome,

    @Schema(description = "Peso da disciplina no edital")
    TipoPeso peso
) {}
