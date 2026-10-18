package br.com.estudos.prova;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import br.com.estudos.prova.ProvaInterna.AlternativaLida;
import br.com.estudos.shared.enums.AtribuicaoQuestao;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.MotivoNaoAvaliavel;
import br.com.estudos.shared.enums.SituacaoGabarito;
import br.com.estudos.shared.enums.StatusProva;
import br.com.estudos.shared.enums.VereditoCobertura;

/**
 * Contratos das rotas públicas da prova (docs/SPRINT-24-PROVA-GABARITO.md §4, §6). Toda rota
 * de questão tem a prova no caminho (D-72). O relatório traz sempre o denominador (D-71) e
 * nunca o enunciado.
 */
public final class ProvaPublica {

    private ProvaPublica() {}

    /** Uma linha de "Verificar com uma prova": a prova, o estado e o andamento do julgamento. */
    public record Resumo(Long id, String nome, String banca, Short ano, String tipo, StatusProva status,
                         SituacaoGabarito gabaritoSituacao, boolean temGabarito, Instant criadoEm,
                         int questoes, int avaliaveis, int julgadas) {}

    /** Uma questão de amostra da conferência: o olho do usuário pega uma troca grosseira de gabarito. */
    public record Amostra(Integer numero, String enunciado, List<AlternativaLida> alternativas,
                          String respostaGabarito, String alternativaGabarito, boolean lidaPorOcr, Integer paginaOrigem) {}

    /** Atribuição que pede o usuário: baixa confiança ou sem assunto (J-6 passo 4). */
    public record Duvidosa(Integer numero, String trecho, AtribuicaoQuestao atribuicao,
                           Long assuntoSugeridoId, String assuntoSugerido) {}

    /** O ponto de conferência da J-6: o que foi lido, antes de gastar IA no julgamento. */
    public record Detalhe(Resumo resumo, FormatoBanca formato, String cargoProva, String gabaritoRecorte,
                          Integer gabaritoCasado, int avaliaveis, Map<MotivoNaoAvaliavel, Integer> naoAvaliaveis,
                          int foraDoEdital, List<Amostra> amostra, List<Duvidosa> duvidosas,
                          StatusProva etapaFalha, String erro) {}

    public record Envio(Resumo prova, boolean criada) {}

    public record CorrecaoRequest(String nome, String banca, Short ano, String tipo) {}

    public record AssuntoDaQuestaoRequest(Long assuntoId) {}

    public record Reanalise(StatusProva status, int aJulgar) {}

    // ------------------------------------------------------------ relatório (§6)

    /** Sempre os dois números (D-71): o contrato nunca traz um percentual sozinho. */
    public record Cobertura(int avaliaveis, int respondiveis, int parciais, int naoRespondiveis, int semVeredito) {}

    /** O que sai do denominador, contado à parte (D-70). */
    public record Declaradas(int foraDoEdital, int anuladas, int semGabarito, int semTexto, int ocrDuvidoso) {}

    public record PorDisciplina(String disciplina, Cobertura cobertura) {}

    public record AssuntoFurado(Long assuntoId, String assunto, String disciplina, int questoes, int parciais,
                                int naoRespondiveis, List<String> faltas) {}

    public record AssuntoSemQuestao(Long assuntoId, String assunto, String disciplina) {}

    public record QuestaoRelatorio(Integer numero, Long assuntoId, String assunto, AtribuicaoQuestao atribuicao,
                                   MotivoNaoAvaliavel motivoNaoAvaliavel, VereditoCobertura veredito,
                                   Long segmentoId, Integer paginaSegmento, String falta,
                                   boolean lidaPorOcr, Integer paginaOrigem) {}

    public record Relatorio(Long provaId, String nome, String banca, Short ano, String tipo, StatusProva status,
                            boolean gabaritoPreliminar, String recorte, Cobertura cobertura, Declaradas declaradas,
                            List<PorDisciplina> porDisciplina, List<AssuntoFurado> assuntosFurados,
                            List<AssuntoSemQuestao> assuntosSemQuestao, List<QuestaoRelatorio> questoes) {}
}
