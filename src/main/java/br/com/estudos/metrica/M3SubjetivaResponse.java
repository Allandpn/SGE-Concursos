package br.com.estudos.metrica;

/**
 * Contagem, não média com sinal como a objetiva — `previsaoReconstrucao` é
 * booleano, e um "desvio médio" de um booleano contra um categórico
 * inventaria precisão (docs/SPRINT-7-METRICAS.md §4.3).
 */
public record M3SubjetivaResponse(int superestimou, int subestimou, int acertouPrevisao, int n) {}
