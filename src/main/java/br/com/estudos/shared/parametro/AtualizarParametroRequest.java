package br.com.estudos.shared.parametro;

import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de PATCH de parâmetro (docs/SPRINT-15-AJUSTES.md §2). */
public record AtualizarParametroRequest(
    @Schema(description = "Novo valor — precisa ser um número positivo (§0 do doc técnico)", example = "80")
    String valor
) {}
