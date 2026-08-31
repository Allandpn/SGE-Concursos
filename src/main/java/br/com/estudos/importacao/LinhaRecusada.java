package br.com.estudos.importacao;

/** Uma linha do CSV que não entrou, e o motivo (docs/SPRINT-2-CADASTRO.md §5.1). */
public record LinhaRecusada(int numeroLinha, String motivo) {}
