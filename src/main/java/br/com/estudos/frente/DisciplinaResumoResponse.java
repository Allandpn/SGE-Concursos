package br.com.estudos.frente;

import io.swagger.v3.oas.annotations.media.Schema;

import br.com.estudos.shared.enums.TipoPeso;

/** Cartão de disciplina em Assuntos (02_JORNADAS §4.5): contagem das fases derivadas, nada novo persistido. */
public record DisciplinaResumoResponse(
    @Schema(description = "Disciplina") Long id,
    @Schema(description = "Nome da disciplina") String nome,
    @Schema(description = "Peso da disciplina no edital do concurso-alvo") TipoPeso peso,
    @Schema(description = "Assuntos ativos ainda não iniciados (D-24)") long backlog,
    @Schema(description = "Assuntos na frente de estudo") long frente,
    @Schema(description = "Assuntos consolidados") long consolidados,
    @Schema(description = "Assuntos ativos da disciplina") long total
) {}
