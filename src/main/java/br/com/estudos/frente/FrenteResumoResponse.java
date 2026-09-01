package br.com.estudos.frente;

/** Resumo da frente de estudo (docs/SPRINT-5-FRENTE.md §2.2), tudo derivado, nada persistido (ADR-033). */
public record FrenteResumoResponse(
    int frenteAtual,
    int tetoGlobalFrente,
    int backlog,
    int consolidados,
    long represado,
    int tetoDiario,
    boolean alertaRepresamento,
    long estimativaDiasParaNormalizar,
    boolean avisoTetoInsuficiente
) {}
