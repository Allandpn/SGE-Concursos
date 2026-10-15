package br.com.estudos.sessao;

import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoSessao;

/** Projeção de um resultado de sessão para D-35 (docs/SPRINT-17-USO-REAL.md §6.1) — sem entidade, sem N+1. */
public interface ResultadoSessaoLinha {
    Long getAssuntoId();
    TipoSessao getTipo();
    ResultadoSessao getResultado();
    Integer getQuestoesTotal();
}
