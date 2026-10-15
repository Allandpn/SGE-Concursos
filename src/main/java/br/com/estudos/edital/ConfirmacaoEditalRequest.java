package br.com.estudos.edital;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import br.com.estudos.shared.enums.TipoPeso;

/**
 * A proposta revisada pelo usuário (D-62, docs/SPRINT-18-CONCURSOS.md §2).
 * Assunto desmarcado não vem no corpo. Nada aqui é confiado: correspondência
 * é revalidada no servidor (D-63).
 */
public record ConfirmacaoEditalRequest(
    @Schema(description = "Nome do concurso", example = "SEFAZ SC 2026") String nome,
    @Schema(description = "Cargo") String cargo,
    @Schema(description = "Banca") String banca,
    @Schema(description = "Data da prova, se conhecida") LocalDate dataProva,
    @Schema(description = "Disciplinas na ordem do edital") List<Disciplina> disciplinas
) {
    public record Disciplina(
        String nome,
        @Schema(description = "Disciplina já existente que é a mesma, ou nulo") Long disciplinaExistenteId,
        @Schema(description = "Peso calculado, ou trocado pelo usuário na revisão") TipoPeso peso,
        List<Assunto> assuntos) {}

    public record Assunto(
        String nome,
        @Schema(description = "Assunto já existente, ou nulo = criar") Long assuntoExistenteId) {}
}
