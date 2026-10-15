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
    @Schema(description = "Pasta do planejamento; nulo no concurso lido por IA, que ainda não passou por lá") String slug,
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
        @Schema(description = "Vazia no assunto criado pela análise por IA: o planejamento atribui o UUID e o SGE adota por nome") String chaveExterna
    ) {}
}
