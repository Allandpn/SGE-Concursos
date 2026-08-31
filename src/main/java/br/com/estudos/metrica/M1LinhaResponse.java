package br.com.estudos.metrica;

import br.com.estudos.shared.enums.FormatoBanca;

/**
 * `disciplinaId` vem nulo na janela GLOBAL (soma entre disciplinas).
 * `percentual` nulo abaixo de n=10 (00_PRODUTO §7 — nunca `0%`).
 * `confiavel` marca n≥100: só aí a tela pode mostrar seta/cor/comparação.
 */
public record M1LinhaResponse(
    Long disciplinaId,
    FormatoBanca formato,
    int acertos,
    int total,
    boolean confiavel,
    Double percentual
) {}
