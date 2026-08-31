package br.com.estudos.metrica;

/**
 * Contagem, não média com sinal como a objetiva — mesmo agora que os dois
 * lados (`previsaoReconstrucao`/`resultado`) têm a mesma escala de três
 * vias, ninguém pediu essa mudança de forma (docs/SPRINT-7-METRICAS.md
 * §4.3).
 */
public record M3SubjetivaResponse(int superestimou, int subestimou, int acertouPrevisao, int n) {}
