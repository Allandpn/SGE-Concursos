package br.com.estudos.disciplina;

import br.com.estudos.shared.enums.TipoPeso;

/** Corpo de POST/PATCH de disciplina (docs/SPRINT-2-CADASTRO.md §4). */
public record DisciplinaRequest(String nome, TipoPeso peso) {}
