package br.com.estudos.conferencia;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

import br.com.estudos.arvore.ArvoreResponses.No;
import br.com.estudos.shared.enums.TipoPeso;

/** Respostas da conferência do edital (docs/SPRINT-25-CONFERENCIA-EDITAL.md §5). */
public final class ConferenciaResponses {

    private ConferenciaResponses() {}

    public record Execucao(String modelo, String data, String arvore) {}

    public record Pendencias(int bloqueiam, int avisam) {}

    public record Resumo(
        Long analiseId, String status, String cargo, String concursoNome, Execucao execucao,
        int disciplinas, int assuntos, int partes,
        @Schema(description = "Partes com nó, por natureza: EXATO, MAIS_AMPLO, PARCIAL") Map<String, Integer> porNatureza,
        @Schema(description = "Partes sem nó (a árvore não tem, ou ainda por decidir)") int semNo,
        Pendencias pendencias,
        @Schema(description = "Partes que a pessoa aceitou ou decidiu") int decididasPorPessoa,
        @Schema(description = "Partes que já vieram resolvidas por uma decisão anterior") int aplicadasDeDecisaoAnterior,
        @Schema(description = "Nenhuma pendência bloqueia (D-91)") boolean podeConcluir) {}

    public record Parte(
        Long id, String chave, int ordem, String texto, String contexto, String herdado, String natureza, String origem,
        String estado, boolean aparece, boolean noMudou, String comentario,
        @Schema(description = "Os nós da ligação vigente") List<No> nos,
        @Schema(description = "Os nós que o sistema propôs e a pessoa não adotou") List<No> propostos,
        String propNatureza) {}

    public record Assunto(
        Long id, String chave, int ordem, String texto, List<String> erros, List<String> alertas, boolean aceito,
        boolean aparece, List<Parte> partes) {}

    public record Disciplina(
        Long id, int ordem, String nome, String restricao, boolean comum, Integer questoes, TipoPeso peso,
        boolean pesoDeterminado, boolean pesoDaPessoa, List<Long> qconcursos, int tentativas, List<Assunto> assuntos) {}

    public record ItemDaFila(
        String tipo, int gravidade, boolean bloqueia, Long disciplinaId, String disciplina, Long assuntoId, String assunto,
        Long parteId, String parte, Long trechoId, List<String> detalhe) {}

    public record Trecho(Long id, Long disciplinaId, String tipo, String estado, String motivo, List<String> linhas) {}

    public record DecisaoResponse(
        Long id, String parte, String contexto, List<No> nos, String natureza, String escopo, Long analiseId, String arvore,
        boolean ativa, String criadaEm, String revogadaEm, String comentario,
        @Schema(description = "Algum nó saiu ou mudou de pai na árvore vigente: reconfirmar (D-90)") boolean precisaReconfirmar) {}
}
