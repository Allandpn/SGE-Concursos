package br.com.estudos.metrica;

/** `mediaDesvio` = previsto − real, com sinal (positivo: superestimou). */
public record M3ObjetivaResponse(double mediaDesvio, int n) {}
