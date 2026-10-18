package br.com.estudos.shared.enums;

/** Estado de uma prova (docs/SPRINT-24-PROVA-GABARITO.md §2.1.1). */
public enum StatusProva {
    LENDO,
    AGUARDANDO_GABARITO,
    CONFERIR,
    JULGANDO,
    PRONTA,
    FALHOU
}
