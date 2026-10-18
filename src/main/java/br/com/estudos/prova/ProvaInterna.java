package br.com.estudos.prova;

import java.util.List;

import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.MotivoNaoAvaliavel;
import br.com.estudos.shared.enums.SituacaoGabarito;
import br.com.estudos.shared.enums.StatusProva;
import br.com.estudos.shared.enums.VereditoCobertura;

/**
 * Contratos das rotas internas da prova, só para o planejamento
 * (docs/SPRINT-24-PROVA-GABARITO.md §3). Quem lê o PDF é o planejamento; o SGE guarda.
 */
public final class ProvaInterna {

    private ProvaInterna() {}

    /** Uma prova que pede trabalho do worker. {@code jaLida}: as questões já estão no SGE (só falta o gabarito). */
    public record Pendente(Long id, StatusProva status, Long concursoId, String nome, String tipo,
                           String gabaritoArquivo, boolean jaLida) {}

    public record AssuntoDaCobertura(String chaveExterna, String nome, String disciplina) {}

    /**
     * O que o worker precisa para ler. Numa prova que já foi lida ({@code jaLida}) ele só lê o
     * gabarito, e para isso conhece o formato, o cargo e os números das questões que já estão aqui.
     */
    public record DadosLeitura(Long id, StatusProva status, Long concursoId, String nome, String tipo,
                               String cargoProva, FormatoBanca formato, String arquivoProva,
                               String gabaritoArquivo, boolean jaLida, List<Integer> numerosDasQuestoes,
                               List<AssuntoDaCobertura> assuntos) {}

    public record AlternativaLida(String letra, String texto) {}

    /**
     * Uma questão como o worker a leu. {@code assuntoChaveExterna} nulo ou fora da cobertura do
     * alvo = fora do edital (D-74). {@code motivoNaoAvaliavel} só aceita {@code SEM_TEXTO} e
     * {@code OCR_DUVIDOSO}: anulada e sem gabarito quem decide é o sistema (D-70).
     */
    public record QuestaoLida(Integer numero, String enunciado, String textoBase, String cabecalhoDisciplina,
                              List<AlternativaLida> alternativas, String assuntoChaveExterna,
                              Boolean baixaConfianca, Boolean lidaPorOcr, Integer paginaOrigem,
                              MotivoNaoAvaliavel motivoNaoAvaliavel) {}

    /** {@code resposta} A–E; {@code anulada} ou {@code *}/{@code X} = anulada (D-70). */
    public record RespostaGabarito(Integer numero, String resposta, Boolean anulada) {}

    public record GabaritoLido(SituacaoGabarito situacao, String recorte, List<RespostaGabarito> respostas) {}

    /**
     * {@code avisoGabarito}: sem gabarito por um motivo (o arquivo não casa com a prova, ou o cargo
     * não foi achado nele) — a prova espera e o usuário vê o motivo em vez de um silêncio.
     */
    public record LeituraRequest(String nome, String banca, Short ano, String tipo, String cargoProva,
                                 FormatoBanca formato, String modelo, List<QuestaoLida> questoes,
                                 GabaritoLido gabarito, String avisoGabarito) {}

    /**
     * O gabarito lido. Sem {@code gabarito} mas com {@code avisoGabarito}, o arquivo enviado não serviu
     * (não casa com a prova, ou o cargo não está nele): a prova volta a esperar, e o usuário vê o motivo.
     */
    public record GabaritoRequest(String modelo, GabaritoLido gabarito, String avisoGabarito) {
        public GabaritoRequest(String modelo, GabaritoLido gabarito) {
            this(modelo, gabarito, null);
        }
    }

    public record Estado(Long id, StatusProva status) {}

    public record SegmentoParaJulgar(Long id, String arquivo, Integer ordem, Integer paginaInicial, Integer paginaFinal) {}

    public record AssuntoParaJulgar(Long id, String chaveExterna, String nome, String disciplina,
                                    List<SegmentoParaJulgar> segmentos) {}

    public record QuestaoParaJulgar(Long id, Integer numero, String enunciado, String textoBase,
                                    List<AlternativaLida> alternativas, String respostaGabarito,
                                    AssuntoParaJulgar assunto) {}

    public record Julgamento(Long id, FormatoBanca formato, List<QuestaoParaJulgar> questoes) {}

    public record ItemVeredito(Integer numero, VereditoCobertura veredito, Long segmentoId,
                               Integer paginaSegmento, String falta) {}

    public record VereditosRequest(String modelo, List<ItemVeredito> itens) {}

    public record ItemRecusado(Integer numero, String motivo) {}

    /** {@code rebaixados}: citação inválida, gravado como {@code NAO_RESPONDIVEL} (D-68). {@code recusados}: nada gravado. */
    public record VereditosResponse(int gravados, List<ItemRecusado> rebaixados, List<ItemRecusado> recusados,
                                    StatusProva status) {}

    public record FalhaRequest(StatusProva etapa, String erro) {}
}
