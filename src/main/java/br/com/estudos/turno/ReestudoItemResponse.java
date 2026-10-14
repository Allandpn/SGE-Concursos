package br.com.estudos.turno;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

/** Um assunto que FALHA devolveu à fila de estudo (D-07/D-11, docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §2.2). */
public record ReestudoItemResponse(
    @Schema(description = "Assunto a reestudar") Long assuntoId,
    @Schema(description = "Nome do assunto") String nome,
    @Schema(description = "Nível da escada em que ficou depois da falha") Integer nivel,
    @Schema(description = "Data da sessão que falhou") LocalDate dataFalha
) {}
