package br.com.estudos.disciplina;

import br.com.estudos.shared.enums.TipoPeso;

/** Corpo de resposta de disciplina (docs/SPRINT-2-CADASTRO.md §4). */
public record DisciplinaResponse(Long id, String nome, TipoPeso peso, boolean ativo) {}
