package br.com.estudos.concurso;

import java.time.LocalDate;
import java.util.List;

import br.com.estudos.shared.enums.TipoPeso;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * O concurso e a cobertura do edital, na forma que o projeto de planejamento
 * lê para triar o acervo (docs/SPRINT-18-CONCURSOS.md §2, "Entrega ao planejamento").
 */
public record PlanejamentoConcursoResponse(
    @Schema(description = "Identificador do concurso (D-64)") String slug,
    @Schema(description = "Nome do concurso", example = "SEFAZ SC 2026") String nome,
    String cargo,
    String banca,
    @Schema(description = "Data da prova, quando conhecida") LocalDate dataProva,
    @Schema(description = "Assuntos do edital, na ordem dele (disciplina, depois ordem)") List<Assunto> assuntos
) {
    public record Assunto(
        String disciplina,
        String assunto,
        TipoPeso peso,
        int ordem,
        @Schema(description = "Identidade do assunto para o planejamento (D-64); vazia só em assunto antigo de importação sem chave") String chaveExterna,
        @Schema(description = "Quantos segmentos o assunto já tem — o planejamento pula quem já tem material") int segmentos,
        @Schema(description = "Motivo registrado de o acervo não ensinar o assunto (D-65); nulo sem lacuna") String lacuna,
        @Schema(description = "O assunto nasceu de uma conferência de edital: o material dele vem do casamento por partes (Sprint 26), não da busca por palavras") boolean viaConferencia
    ) {}
}
