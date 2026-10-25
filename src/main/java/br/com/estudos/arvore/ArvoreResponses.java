package br.com.estudos.arvore;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** Respostas da árvore de assuntos do QConcursos (docs/SPRINT-25-CONFERENCIA-EDITAL.md §2.2 e §5). */
public final class ArvoreResponses {

    private ArvoreResponses() {}

    public record No(
        @Schema(description = "Id do nó; o da disciplina é o id do QConcursos negado (ids de disciplina e de assunto colidem)") long id,
        String nome,
        @Schema(description = "Da disciplina até o nó, incluindo o nó") List<String> caminho,
        @Schema(description = "Quantos filhos diretos tem; ajuda a perceber se o nó é largo demais") int filhos,
        @Schema(description = "A disciplina (nó raiz) a que pertence") long raizId,
        @Schema(description = "Rótulo da versão da árvore em que o nó foi lido") String versao) {}

    public record Versao(
        long id, String rotulo, String fonte, LocalDate data, int quantidadeNos, boolean vigente, Instant carregadaEm,
        @Schema(description = "Contagem por tipo contra a versão vigente quando foi carregada: NOVO, REMOVIDO, RENOMEADO, MOVIDO")
        Map<String, Integer> diferencas) {}

    public record Diferenca(
        String tipo, long noId, String nome, String nomeAnterior, Long paiAnteriorId, Long paiId) {}
}
