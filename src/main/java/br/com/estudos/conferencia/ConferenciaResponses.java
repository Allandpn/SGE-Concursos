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
        @Schema(description = "Partes já decididas por você em que a IA, na última leitura, propôs outro nó: a sua decisão foi mantida (D-95)") int iaMudou,
        @Schema(description = "Nenhuma pendência bloqueia (D-91)") boolean podeConcluir) {}

    public record Parte(
        Long id, String chave, int ordem, String texto, String contexto, String herdado, String natureza, String origem,
        String estado, boolean aparece, boolean noMudou, boolean iaMudou, String comentario,
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

    /** O que "valer para todos os editais" mudaria: partes de outros editais, ainda por conferir, com a chave igual. */
    public record EscopoTodos(int partes, List<String> exemplos) {}

    public record Trecho(Long id, Long disciplinaId, String tipo, String estado, String motivo, List<String> linhas) {}

    /** Uma parte do assunto como o edital a dividiu, com o aviso que ainda pede atenção (docs §9). */
    public record ParteDoAssunto(
        Long parteId, Long analiseId, String texto, String contexto, String herdado, String natureza, List<No> nos,
        @Schema(description = "SEM_NO, MAIS_AMPLO, PARCIAL ou NO_MUDOU; nulo = sem aviso") String aviso) {}

    public record AvisosDoConcurso(
        Long concursoId, Long analiseId,
        @Schema(description = "Partes sem filtro de questões: sem nó (a árvore não tem, ou ainda por decidir)") int partesSemNo,
        int vinculosAmplosOuParciais, int nosQueMudaram) {}

    public record DecisaoResponse(
        Long id, String parte, String contexto, List<No> nos, String natureza, String escopo, Long analiseId, String arvore,
        boolean ativa, String criadaEm, String revogadaEm, String comentario,
        @Schema(description = "Algum nó saiu ou mudou de pai na árvore vigente: reconfirmar (D-90)") boolean precisaReconfirmar) {}
}
