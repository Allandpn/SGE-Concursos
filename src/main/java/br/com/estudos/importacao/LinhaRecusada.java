package br.com.estudos.importacao;

import io.swagger.v3.oas.annotations.media.Schema;

/** Uma linha do CSV que não entrou, e o motivo (docs/SPRINT-2-CADASTRO.md §5.1). */
public record LinhaRecusada(
    @Schema(description = "Número da linha no arquivo CSV, 1-indexado (cabeçalho não conta)") int numeroLinha,
    @Schema(description = "Motivo da recusa") String motivo
) {}
