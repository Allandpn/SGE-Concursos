package br.com.estudos.revisao;

import br.com.estudos.shared.enums.ResultadoSessao;

/**
 * Projeção de uma revisão cumprida para a fase em lote
 * (docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §3) — sem entidade, sem N+1.
 */
public interface ResultadoCumpridoLinha {
    Long getAssuntoId();
    Integer getNivel();
    ResultadoSessao getResultado();
}
