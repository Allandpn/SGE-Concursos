package br.com.estudos.shared.parametro;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de resposta de parâmetro (docs/SPRINT-15-AJUSTES.md §2). */
public record ParametroResponse(
    @Schema(description = "Identificador do parâmetro") String chave,
    @Schema(description = "Valor atual, sempre texto — quem lê decide o tipo") String valor,
    @Schema(description = "O que este parâmetro controla, e onde") String descricao,
    @Schema(description = "Última atualização") Instant atualizadoEm
) {}
