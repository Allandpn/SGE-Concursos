package br.com.estudos.metrica;

/** `percentual` nulo quando ainda não há nenhuma revisão CUMPRIDA. */
public record M4Response(Double percentual, int dentroDoPrazo, int totalCumpridas) {}
