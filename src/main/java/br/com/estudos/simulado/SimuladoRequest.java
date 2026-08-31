package br.com.estudos.simulado;

import java.time.LocalDate;
import java.util.List;

/** Corpo de POST de simulado (docs/SPRINT-8-SIMULADO.md §5). Tudo-ou-nada (§3). */
public record SimuladoRequest(
    LocalDate data,
    Integer duracaoMinutos,
    List<ResultadoSimuladoRequest> resultados
) {}
