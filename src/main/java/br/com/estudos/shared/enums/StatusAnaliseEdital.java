package br.com.estudos.shared.enums;

/** Estados da análise de edital (docs/SPRINT-18-CONCURSOS.md §4.1). */
public enum StatusAnaliseEdital {
    LENDO_EDITAL,
    ESCOLHER_CARGO,
    SEPARANDO_ASSUNTOS,
    PRONTA,
    CONFIRMADA,
    FALHOU
}
