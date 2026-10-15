package br.com.estudos.sessao;

import java.time.LocalDate;

/** Data da primeira sessão de cada assunto — "quantos entraram" do balanço semanal (02_JORNADAS J-3). */
public interface PrimeiraSessaoLinha {
    Long getAssuntoId();
    LocalDate getPrimeira();
}
