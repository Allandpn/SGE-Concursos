package br.com.estudos.frente;

import br.com.estudos.shared.enums.FaseAssunto;
import io.swagger.v3.oas.annotations.media.Schema;

/** docs/SPRINT-5-FRENTE.md §3. */
public record AssuntoFaseResponse(
    @Schema(description = "Assunto consultado") Long assuntoId,
    @Schema(description = "Fase derivada — nunca persistida (D-16)") FaseAssunto fase
) {}
