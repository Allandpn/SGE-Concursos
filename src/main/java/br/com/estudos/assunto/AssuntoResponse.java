package br.com.estudos.assunto;

import br.com.estudos.shared.enums.TipoPeso;

/** Corpo de resposta de assunto (docs/SPRINT-2-CADASTRO.md §4). */
public record AssuntoResponse(
    Long id,
    Long disciplinaId,
    String nome,
    TipoPeso peso,
    Short dificuldadePercebida,
    Integer ordem,
    boolean ativo
) {}
