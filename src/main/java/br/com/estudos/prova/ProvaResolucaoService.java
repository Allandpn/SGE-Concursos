package br.com.estudos.prova;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.prova.ProvaInterna.AlternativaLida;
import br.com.estudos.prova.ProvaPublica.AbrirTentativaRequest;
import br.com.estudos.prova.ProvaPublica.CorrecaoDaQuestao;
import br.com.estudos.prova.ProvaPublica.Discordancia;
import br.com.estudos.prova.ProvaPublica.ForaDaNota;
import br.com.estudos.prova.ProvaPublica.Nota;
import br.com.estudos.prova.ProvaPublica.Placar;
import br.com.estudos.prova.ProvaPublica.PlacarDisciplina;
import br.com.estudos.prova.ProvaPublica.QuestaoDaTentativa;
import br.com.estudos.prova.ProvaPublica.RespostaRequest;
import br.com.estudos.prova.ProvaPublica.TentativaAberta;
import br.com.estudos.prova.ProvaPublica.TentativaDetalhe;
import br.com.estudos.prova.ProvaPublica.TentativaResumo;
import br.com.estudos.prova.ProvaPublica.TipoDiscordancia;
import br.com.estudos.simulado.ResultadoSimuladoRepository;
import br.com.estudos.simulado.ResultadoSimuladoRequest;
import br.com.estudos.simulado.SimuladoRequest;
import br.com.estudos.simulado.SimuladoService;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.MotivoNaoAvaliavel;
import br.com.estudos.shared.enums.SituacaoGabarito;
import br.com.estudos.shared.enums.VereditoCobertura;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Resolver a prova (J-7; 01_DOMINIO §3.11; docs/SPRINT-24-PROVA-GABARITO.md §4, §7). O usuário
 * responde a prova inteira; o sistema corrige **pelo gabarito, nunca pela IA** (D-80) e a mesma
 * tentativa conta uma vez. A nota nunca é persistida (D-16): sai das respostas e do gabarito na
 * hora, e só o {@code resultado_simulado} da Sprint 8 guarda o que M-1 precisa — por disciplina,
 * da primeira tentativa (D-78). **Nenhuma sessão, revisão ou mudança de turno nasce daqui.**
 */
@Service
public class ProvaResolucaoService {

    private static final Logger log = LoggerFactory.getLogger(ProvaResolucaoService.class);
    private static final Set<String> LETRAS = Set.of("A", "B", "C", "D", "E");
    private static final Set<String> CERTO_ERRADO = Set.of("C", "E");

    private final ProvaRepository provaRepository;
    private final QuestaoRepository questaoRepository;
    private final VereditoQuestaoRepository vereditoRepository;
    private final TentativaProvaRepository tentativaRepository;
    private final RespostaProvaRepository respostaRepository;
    private final SimuladoService simuladoService;
    private final ResultadoSimuladoRepository resultadoSimuladoRepository;
    private final Clock clock;

    public ProvaResolucaoService(
            ProvaRepository provaRepository,
            QuestaoRepository questaoRepository,
            VereditoQuestaoRepository vereditoRepository,
            TentativaProvaRepository tentativaRepository,
            RespostaProvaRepository respostaRepository,
            SimuladoService simuladoService,
            ResultadoSimuladoRepository resultadoSimuladoRepository,
            Clock clock) {
        this.provaRepository = provaRepository;
        this.questaoRepository = questaoRepository;
        this.vereditoRepository = vereditoRepository;
        this.tentativaRepository = tentativaRepository;
        this.respostaRepository = respostaRepository;
        this.simuladoService = simuladoService;
        this.resultadoSimuladoRepository = resultadoSimuladoRepository;
        this.clock = clock;
    }

    /** O que o relatório mostra da resolução: a nota da tentativa que conta, as outras e as discordâncias. */
    public record NotaDaProva(Nota nota, List<TentativaResumo> outras, List<Discordancia> discordancias) {}

    // ------------------------------------------------------------ abrir ou retomar (J-7 passos 1-2)

    /**
     * Abre a tentativa, ou a retoma pelo {@code tentativaId} que o cliente gerou ao abrir a tela
     * (D-80). Sem gabarito a prova não é resolvida (D-81).
     */
    @Transactional
    public TentativaAberta abrir(Long provaId, AbrirTentativaRequest pedido) {
        if (pedido.tentativaId() == null) {
            throw new ValidationException("Informe o tentativaId, gerado ao abrir a tela.", "TENTATIVA_OBRIGATORIA", "tentativaId");
        }
        var prova = buscarProva(provaId);
        var existente = tentativaRepository.findByTentativaId(pedido.tentativaId());
        if (existente.isPresent()) {
            if (!existente.get().getProva().getId().equals(provaId)) {
                throw new ConflictException("Este tentativaId pertence a outra prova.", "TENTATIVA_DE_OUTRA_PROVA");
            }
            return new TentativaAberta(resumo(existente.get(), prova), false);
        }
        if (prova.getGabaritoSituacao() == null || prova.getFormato() == null) {
            throw new ConflictException("Sem gabarito, a prova não pode ser resolvida (D-81).", "PROVA_SEM_GABARITO");
        }
        var cronometrada = Boolean.TRUE.equals(pedido.cronometrada());
        if (pedido.limiteMinutos() != null && pedido.limiteMinutos() < 1) {
            throw new ValidationException("O limite de tempo precisa ser de pelo menos 1 minuto.", "LIMITE_INVALIDO", "limiteMinutos");
        }
        var tentativa = tentativaRepository.save(new TentativaProva(prova, pedido.tentativaId(), Instant.now(clock),
            cronometrada, pedido.limiteMinutos()));
        return new TentativaAberta(resumo(tentativa, prova), true);
    }

    // ------------------------------------------------------------ resolver

    /**
     * As questões como o usuário as vê — nunca a resposta do gabarito. A lista {@code correcao}
     * (resposta do gabarito, acerto, o que sustentava) só se enche depois da entrega (D-80).
     */
    @Transactional(readOnly = true)
    public TentativaDetalhe detalhe(Long provaId, UUID tentativaId) {
        var tentativa = buscarTentativa(provaId, tentativaId);
        var prova = tentativa.getProva();
        var questoes = questaoRepository.findByProvaIdOrderByNumeroAsc(provaId);
        var respostas = respostasPorQuestao(tentativa);
        var lista = new ArrayList<QuestaoDaTentativa>();
        for (var q : questoes) {
            var r = respostas.get(q.getId());
            lista.add(new QuestaoDaTentativa(q.getNumero(), q.getEnunciado(), q.getTextoBase(), q.getCabecalhoDisciplina(),
                q.getAlternativas().stream().map(a -> new AlternativaLida(a.getLetra(), a.getTexto())).toList(),
                q.avaliavel(), r == null ? null : r.getAlternativa(), r != null && r.isMarcadaRevisar(),
                q.isLidaPorOcr(), q.getPaginaOrigem()));
        }
        var correcao = tentativa.entregue() ? correcao(prova, questoes, respostas) : List.<CorrecaoDaQuestao>of();
        return new TentativaDetalhe(resumo(tentativa, prova, questoes.size(), respostas), prova.getFormato(), lista, correcao);
    }

    /** Grava (ou limpa) a alternativa e a marca de revisar; a tentativa entregue não muda. */
    @Transactional
    public void responder(Long provaId, UUID tentativaId, Integer numero, RespostaRequest pedido) {
        var tentativa = buscarTentativa(provaId, tentativaId);
        if (tentativa.entregue()) {
            throw new ConflictException("A tentativa já foi entregue.", "TENTATIVA_ENTREGUE");
        }
        var questao = questaoRepository.findByProvaIdAndNumero(provaId, numero)
            .orElseThrow(() -> new NotFoundException("Questão não encontrada nesta prova.", "QUESTAO_INEXISTENTE"));
        var alternativa = alternativaValida(tentativa.getProva().getFormato(), questao, pedido.alternativa());
        var resposta = respostaRepository.findByTentativaIdAndQuestaoId(tentativa.getId(), questao.getId())
            .orElseGet(() -> new RespostaProva(tentativa, questao));
        resposta.setAlternativa(alternativa);
        resposta.setMarcadaRevisar(Boolean.TRUE.equals(pedido.marcadaRevisar()));
        resposta.setRespondidaEm(Instant.now(clock));
        respostaRepository.save(resposta);
    }

    private String alternativaValida(FormatoBanca formato, Questao questao, String bruta) {
        if (bruta == null || bruta.isBlank()) return null;
        var letra = bruta.strip().toUpperCase(java.util.Locale.ROOT);
        var permitidas = formato == FormatoBanca.CERTO_ERRADO ? CERTO_ERRADO : LETRAS;
        var existe = questao.getAlternativas().isEmpty()
            || questao.getAlternativas().stream().anyMatch(a -> a.getLetra().equals(letra));
        if (!permitidas.contains(letra) || !existe) {
            throw new ValidationException("Alternativa inválida para a questão " + questao.getNumero() + ": '" + bruta + "'.",
                "LETRA_INVALIDA", "alternativa");
        }
        return letra;
    }

    // ------------------------------------------------------------ entregar (J-7 passo 3, D-78)

    /**
     * Corrige pelo gabarito e devolve a nota. **Idempotente:** entregar de novo devolve a mesma
     * nota, sem outro simulado (D-80). Na primeira tentativa entregue da prova — e só nela —,
     * cria o {@code Simulado} com um {@code ResultadoSimulado} por disciplina (D-78); o índice
     * único parcial do banco garante que é uma só, mesmo com duas entregas simultâneas.
     */
    @Transactional
    public Nota entregar(Long provaId, UUID tentativaId) {
        var tentativa = buscarTentativa(provaId, tentativaId);
        var prova = tentativa.getProva();
        var questoes = questaoRepository.findByProvaIdOrderByNumeroAsc(provaId);
        var calculo = calcular(questoes, respostasPorQuestao(tentativa));
        if (!tentativa.entregue()) {
            var agora = Instant.now(clock);
            tentativa.setEntregueEm(agora);
            var conta = !tentativaRepository.existsByProvaIdAndSimuladoIsNotNull(provaId) && !calculo.porDisciplina().isEmpty();
            if (conta) {
                var minutos = (int) Math.max(1, Duration.between(tentativa.getIniciadaEm(), agora).toMinutes());
                var resultados = calculo.porDisciplina().values().stream()
                    .map(p -> new ResultadoSimuladoRequest(p.disciplinaId(), prova.getFormato(), p.placar().corretas(), p.placar().total()))
                    .toList();
                var registrado = simuladoService.registrar(new SimuladoRequest(LocalDate.now(clock), minutos, resultados));
                tentativa.setSimulado(registrado.simulado());
            }
            tentativaRepository.saveAndFlush(tentativa);
        }
        return nota(prova, tentativa, calculo);
    }

    // ------------------------------------------------------------ nota no relatório (§6)

    /** A nota da tentativa que conta, as outras tentativas e onde a cobertura e a nota discordam. */
    @Transactional(readOnly = true)
    public Optional<NotaDaProva> notaDaProva(Prova prova, List<Questao> questoes, Map<Long, VereditoQuestao> vigentes) {
        var contaOpt = tentativaRepository.findFirstByProvaIdAndSimuladoIsNotNull(prova.getId());
        if (contaOpt.isEmpty()) return Optional.empty();
        var conta = contaOpt.get();
        var respostas = respostasPorQuestao(conta);
        var calculo = calcular(questoes, respostas);

        var outras = tentativaRepository.findByProvaIdOrderByIdAsc(prova.getId()).stream()
            .filter(t -> !t.getId().equals(conta.getId()))
            .map(t -> resumo(t, prova))
            .toList();
        return Optional.of(new NotaDaProva(nota(prova, conta, calculo), outras, discordancias(questoes, vigentes, calculo)));
    }

    /**
     * Onde a cobertura prevista e a nota real discordam, por assunto: o material parecia sólido e o
     * usuário errou (o material ensina menos do que parece), ou parecia furado e o usuário acertou
     * tudo (o julgamento está severo). Informação, não gatilho (D-78).
     */
    private List<Discordancia> discordancias(List<Questao> questoes, Map<Long, VereditoQuestao> vigentes, Calculo calculo) {
        var porAssunto = new LinkedHashMap<Long, List<Questao>>();
        for (var q : questoes) {
            if (calculo.acertou().containsKey(q.getNumero())) {
                porAssunto.computeIfAbsent(q.getAssunto().getId(), a -> new ArrayList<>()).add(q);
            }
        }
        var lista = new ArrayList<Discordancia>();
        for (var grupo : porAssunto.values()) {
            var vereditos = grupo.stream().map(q -> vigentes.get(q.getId())).toList();
            if (vereditos.stream().anyMatch(v -> v == null)) continue; // ainda sem julgamento: não há o que comparar
            var solida = vereditos.stream().allMatch(v -> v.getVeredito() == VereditoCobertura.RESPONDIVEL);
            var furada = vereditos.stream().anyMatch(v -> v.getVeredito() != VereditoCobertura.RESPONDIVEL);
            var acertos = (int) grupo.stream().filter(q -> calculo.acertou().get(q.getNumero())).count();
            var assunto = grupo.get(0).getAssunto();
            TipoDiscordancia tipo = null;
            if (solida && acertos < grupo.size()) tipo = TipoDiscordancia.SOLIDA_E_ERROU;
            if (furada && acertos == grupo.size()) tipo = TipoDiscordancia.FURADA_E_ACERTOU;
            if (tipo != null) {
                lista.add(new Discordancia(assunto.getId(), assunto.getNome(), assunto.getDisciplina().getNome(), tipo,
                    grupo.size(), acertos));
            }
        }
        return lista;
    }

    // ------------------------------------------------------------ gabarito definitivo (D-84)

    /**
     * Gabarito definitivo que muda ou anula uma resposta: recalcula a nota da tentativa que conta
     * e atualiza o {@code resultado_simulado} dela — a única exceção a "resultado de simulado não
     * muda". A disciplina que perde todas as questões deixa de ter apuração (a linha é removida,
     * porque {@code questoes_total} não pode ser zero; é dado derivado, recriável).
     */
    @Transactional
    public void recalcularNotaQueConta(Prova prova) {
        var contaOpt = tentativaRepository.findFirstByProvaIdAndSimuladoIsNotNull(prova.getId());
        if (contaOpt.isEmpty()) return;
        var conta = contaOpt.get();
        var calculo = calcular(questaoRepository.findByProvaIdOrderByNumeroAsc(prova.getId()), respostasPorQuestao(conta));
        for (var resultado : resultadoSimuladoRepository.findBySimuladoId(conta.getSimulado().getId())) {
            var novo = calculo.porDisciplina().get(resultado.getDisciplina().getId());
            if (novo == null) {
                resultadoSimuladoRepository.delete(resultado);
            } else {
                resultado.setQuestoesCorretas(novo.placar().corretas());
                resultado.setQuestoesTotal(novo.placar().total());
            }
        }
        log.info("D-84: nota da prova {} recalculada com o gabarito {}.", prova.getId(), prova.getGabaritoSituacao());
    }

    // ------------------------------------------------------------ cálculo (derivado, D-16)

    private record Calculo(Placar global, Map<Long, PlacarDisciplina> porDisciplina, ForaDaNota foraDaNota,
                           int semResposta, Map<Integer, Boolean> acertou) {}

    /**
     * Entram na nota as questões avaliáveis com assunto atribuído — a mesma base do denominador da
     * cobertura (D-70). As demais saem e vêm contadas à parte. Questão sem resposta conta no total.
     */
    private Calculo calcular(List<Questao> questoes, Map<Long, RespostaProva> respostas) {
        var disciplinas = new LinkedHashMap<Long, int[]>();
        var nomes = new HashMap<Long, String>();
        var acertou = new LinkedHashMap<Integer, Boolean>();
        var foraDoEdital = 0;
        var anuladas = 0;
        var semGabarito = 0;
        var semTexto = 0;
        var ocrDuvidoso = 0;
        var semResposta = 0;
        var corretas = 0;
        var total = 0;
        for (var q : questoes) {
            if (q.getMotivoNaoAvaliavel() != null) {
                switch (q.getMotivoNaoAvaliavel()) {
                    case ANULADA -> anuladas++;
                    case SEM_GABARITO -> semGabarito++;
                    case SEM_TEXTO -> semTexto++;
                    case OCR_DUVIDOSO -> ocrDuvidoso++;
                }
                continue;
            }
            if (q.getAssunto() == null) {
                foraDoEdital++;
                continue;
            }
            var r = respostas.get(q.getId());
            var marcada = r == null ? null : r.getAlternativa();
            if (marcada == null) semResposta++;
            var acerto = marcada != null && marcada.equals(q.getRespostaGabarito());
            var disciplina = q.getAssunto().getDisciplina();
            nomes.putIfAbsent(disciplina.getId(), disciplina.getNome());
            var contagem = disciplinas.computeIfAbsent(disciplina.getId(), d -> new int[2]);
            contagem[1]++;
            total++;
            if (acerto) {
                contagem[0]++;
                corretas++;
            }
            acertou.put(q.getNumero(), acerto);
        }
        var porDisciplina = new LinkedHashMap<Long, PlacarDisciplina>();
        disciplinas.forEach((id, c) -> porDisciplina.put(id, new PlacarDisciplina(id, nomes.get(id), new Placar(c[0], c[1]))));
        return new Calculo(new Placar(corretas, total), porDisciplina,
            new ForaDaNota(foraDoEdital, anuladas, semGabarito, semTexto, ocrDuvidoso), semResposta, acertou);
    }

    private Nota nota(Prova prova, TentativaProva tentativa, Calculo c) {
        return new Nota(tentativa.getEntregueEm(), tentativa.getSimulado() != null,
            prova.getGabaritoSituacao() == SituacaoGabarito.PRELIMINAR, c.global(),
            List.copyOf(c.porDisciplina().values()), c.foraDaNota(), c.semResposta());
    }

    private List<CorrecaoDaQuestao> correcao(Prova prova, List<Questao> questoes, Map<Long, RespostaProva> respostas) {
        var vigentes = new HashMap<Long, VereditoQuestao>();
        vereditoRepository.vigentesDaProva(prova.getId()).forEach(v -> vigentes.put(v.getQuestao().getId(), v));
        var calculo = calcular(questoes, respostas);
        var lista = new ArrayList<CorrecaoDaQuestao>();
        for (var q : questoes) {
            var r = respostas.get(q.getId());
            var v = vigentes.get(q.getId());
            var valeNaNota = calculo.acertou().containsKey(q.getNumero());
            lista.add(new CorrecaoDaQuestao(q.getNumero(), q.getRespostaGabarito(), r == null ? null : r.getAlternativa(),
                valeNaNota ? calculo.acertou().get(q.getNumero()) : null, valeNaNota,
                v == null ? null : v.getVeredito(),
                v == null || v.getSegmento() == null ? null : v.getSegmento().getId(),
                v == null ? null : v.getPaginaSegmento(), v == null ? null : v.getFalta()));
        }
        return lista;
    }

    // ------------------------------------------------------------ internos

    private Prova buscarProva(Long id) {
        return provaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Prova não encontrada.", "PROVA_INEXISTENTE"));
    }

    private TentativaProva buscarTentativa(Long provaId, UUID tentativaId) {
        return tentativaRepository.findByTentativaId(tentativaId)
            .filter(t -> t.getProva().getId().equals(provaId))
            .orElseThrow(() -> new NotFoundException("Tentativa não encontrada nesta prova.", "TENTATIVA_INEXISTENTE"));
    }

    private Map<Long, RespostaProva> respostasPorQuestao(TentativaProva tentativa) {
        var mapa = new HashMap<Long, RespostaProva>();
        respostaRepository.findByTentativaId(tentativa.getId()).forEach(r -> mapa.put(r.getQuestao().getId(), r));
        return mapa;
    }

    private TentativaResumo resumo(TentativaProva t, Prova prova) {
        return resumo(t, prova, questaoRepository.findByProvaIdOrderByNumeroAsc(prova.getId()).size(), respostasPorQuestao(t));
    }

    private TentativaResumo resumo(TentativaProva t, Prova prova, int questoes, Map<Long, RespostaProva> respostas) {
        var respondidas = (int) respostas.values().stream().filter(r -> r.getAlternativa() != null).count();
        return new TentativaResumo(t.getId(), t.getTentativaId(), prova.getId(), t.getIniciadaEm(), t.getEntregueEm(),
            t.isCronometrada(), t.getLimiteMinutos(), questoes, respondidas, t.getSimulado() != null);
    }
}
