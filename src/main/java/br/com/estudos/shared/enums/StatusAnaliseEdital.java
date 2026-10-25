package br.com.estudos.shared.enums;

/** Estados da análise de edital (docs/SPRINT-25-CONFERENCIA-EDITAL.md §2.1). */
public enum StatusAnaliseEdital {
    LENDO_EDITAL,
    ESCOLHER_CARGO,
    SEPARANDO_ASSUNTOS,
    EM_CONFERENCIA,
    CONFIRMADA,
    FALHOU
}
