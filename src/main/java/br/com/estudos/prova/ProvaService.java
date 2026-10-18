package br.com.estudos.prova;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.concurso.CoberturaEditalRepository;
import br.com.estudos.concurso.ConcursoRepository;
import br.com.estudos.prova.ProvaInterna.AlternativaLida;
import br.com.estudos.prova.ProvaInterna.AssuntoDaCobertura;
import br.com.estudos.prova.ProvaInterna.AssuntoParaJulgar;
import br.com.estudos.prova.ProvaInterna.DadosLeitura;
import br.com.estudos.prova.ProvaInterna.Estado;
import br.com.estudos.prova.ProvaInterna.FalhaRequest;
import br.com.estudos.prova.ProvaInterna.GabaritoLido;
import br.com.estudos.prova.ProvaInterna.GabaritoRequest;
import br.com.estudos.prova.ProvaInterna.ItemRecusado;
import br.com.estudos.prova.ProvaInterna.ItemVeredito;
import br.com.estudos.prova.ProvaInterna.Julgamento;
import br.com.estudos.prova.ProvaInterna.LeituraRequest;
import br.com.estudos.prova.ProvaInterna.Pendente;
import br.com.estudos.prova.ProvaInterna.QuestaoLida;
import br.com.estudos.prova.ProvaInterna.QuestaoParaJulgar;
import br.com.estudos.prova.ProvaInterna.RespostaGabarito;
import br.com.estudos.prova.ProvaInterna.SegmentoParaJulgar;
import br.com.estudos.prova.ProvaInterna.VereditosRequest;
import br.com.estudos.prova.ProvaInterna.VereditosResponse;
import br.com.estudos.segmento.Segmento;
import br.com.estudos.segmento.SegmentoRepository;
import br.com.estudos.shared.enums.AtribuicaoQuestao;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.MotivoNaoAvaliavel;
import br.com.estudos.shared.enums.SituacaoGabarito;
import br.com.estudos.shared.enums.StatusProva;
import br.com.estudos.shared.enums.VereditoCobertura;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * A prova hospedada (01_DOMINIO §3.11, D-67 a D-85; docs/SPRINT-24-PROVA-GABARITO.md §3).
 * Esta classe cobre o lado do planejamento — leitura, gabarito, julgamento —, que lê o PDF
 * e entrega ao SGE. A IA lê e julga; o sistema valida, conta e calcula (D-67).
 */
@Service
public class ProvaService {

    private static final Set<String> LETRAS = Set.of("A", "B", "C", "D", "E");
    private static final Set<String> CERTO_ERRADO = Set.of("C", "E");
    private static final Set<String> ANULADA = Set.of("*", "X");
    private static final int ERRO_MAX = 2000;

    private final ProvaRepository provaRepository;
    private final QuestaoRepository questaoRepository;
    private final VereditoQuestaoRepository vereditoRepository;
    private final ConcursoRepository concursoRepository;
    private final CoberturaEditalRepository coberturaRepository;
    private final SegmentoRepository segmentoRepository;
    private final ProvaResolucaoService resolucaoService;

    public ProvaService(
            ProvaRepository provaRepository,
            QuestaoRepository questaoRepository,
            VereditoQuestaoRepository vereditoRepository,
            ConcursoRepository concursoRepository,
            CoberturaEditalRepository coberturaRepository,
            SegmentoRepository segmentoRepository,
            ProvaResolucaoService resolucaoService) {
        this.provaRepository = provaRepository;
        this.questaoRepository = questaoRepository;
        this.vereditoRepository = vereditoRepository;
        this.concursoRepository = concursoRepository;
        this.coberturaRepository = coberturaRepository;
        this.segmentoRepository = segmentoRepository;
        this.resolucaoService = resolucaoService;
    }

    // ------------------------------------------------------------ registro

    /** Registra o envio; a mesma prova ao mesmo concurso é uma só (idempotência, como D-45). */
    @Transactional
    public Prova registrar(Long concursoId, String nome, String hashProva, String tipo, String gabaritoArquivo) {
        var concurso = concursoRepository.findById(concursoId)
            .orElseThrow(() -> new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE"));
        return provaRepository.findByConcursoIdAndHashProva(concursoId, hashProva)
            .orElseGet(() -> provaRepository.save(new Prova(concurso, nome, hashProva, tipo, gabaritoArquivo)));
    }

    // ------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public List<Pendente> pendentes() {
        return provaRepository.findByStatusInOrderByIdAsc(EnumSet.of(StatusProva.LENDO, StatusProva.JULGANDO)).stream()
            .map(p -> new Pendente(p.getId(), p.getStatus(), p.getConcurso().getId(), p.getNome(), p.getTipo(),
                p.getGabaritoArquivo(), questaoRepository.existsByProvaId(p.getId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public DadosLeitura dadosLeitura(Long id) {
        var prova = buscar(id);
        exigir(prova, StatusProva.LENDO);
        var assuntos = coberturaDoAlvo(prova).values().stream()
            .map(c -> new AssuntoDaCobertura(c.assunto().getChaveExterna(), c.assunto().getNome(), c.disciplina()))
            .toList();
        var numeros = questaoRepository.findByProvaIdOrderByNumeroAsc(id).stream().map(Questao::getNumero).toList();
        return new DadosLeitura(prova.getId(), prova.getStatus(), prova.getConcurso().getId(), prova.getNome(),
            prova.getTipo(), prova.getCargoProva(), prova.getFormato(), "entrada/" + prova.getId() + "/prova.pdf",
            prova.getGabaritoArquivo(), !numeros.isEmpty(), numeros, assuntos);
    }

    /**
     * Entrega as questões lidas, e o gabarito se houver. Sem gabarito, a prova espera (D-81);
     * com gabarito, vai para a conferência do usuário (J-6 passo 3).
     */
    @Transactional
    public Estado entregarLeitura(Long id, LeituraRequest pedido) {
        var prova = buscar(id);
        exigir(prova, StatusProva.LENDO);
        if (questaoRepository.existsByProvaId(id)) {
            throw new ConflictException("As questões desta prova já foram lidas; envie só o gabarito.", "QUESTOES_JA_LIDAS");
        }
        if (pedido.questoes() == null || pedido.questoes().isEmpty()) {
            throw new ValidationException("A leitura não trouxe nenhuma questão.", "SEM_QUESTOES", "questoes");
        }
        if (pedido.formato() == null) {
            throw new ValidationException("Informe o formato da prova.", "FORMATO_OBRIGATORIO", "formato");
        }
        var numeros = new HashSet<Integer>();
        for (var lida : pedido.questoes()) {
            if (lida.numero() == null || lida.numero() < 1 || !numeros.add(lida.numero())) {
                throw new ValidationException("Numeração de questões inválida ou repetida.", "NUMERACAO_INVALIDA", "questoes");
            }
        }

        if (pedido.nome() != null && !pedido.nome().isBlank()) prova.setNome(pedido.nome().strip());
        if (pedido.banca() != null) prova.setBanca(pedido.banca());
        if (pedido.ano() != null) prova.setAno(pedido.ano());
        if (pedido.tipo() != null && !pedido.tipo().isBlank()) prova.setTipo(pedido.tipo().strip());
        if (pedido.cargoProva() != null) prova.setCargoProva(pedido.cargoProva());
        prova.setFormato(pedido.formato());
        prova.setModelo(pedido.modelo());

        var cobertura = coberturaDoAlvo(prova);
        var questoes = new ArrayList<Questao>();
        for (var lida : pedido.questoes()) {
            questoes.add(criarQuestao(prova, pedido.formato(), lida, cobertura));
        }
        // O gabarito entra antes de gravar: o banco recusa questão avaliável sem resposta (D-70).
        aplicarGabarito(prova, questoes, pedido.gabarito());
        questaoRepository.saveAll(questoes);
        // Sem gabarito por um motivo: a prova espera e o usuário vê o motivo (J-6 falhas).
        prova.setErro(pedido.gabarito() == null ? limitar(pedido.avisoGabarito()) : null);
        prova.setStatus(pedido.gabarito() == null ? StatusProva.AGUARDANDO_GABARITO : StatusProva.CONFERIR);
        return new Estado(prova.getId(), prova.getStatus());
    }

    /**
     * Entrega só o gabarito: de uma prova já lida que esperava por ele, ou o definitivo que
     * substitui o preliminar (D-84) — este devolve ao julgamento as questões cuja resposta mudou.
     * A nota da tentativa que conta é recalculada pela resolução (D-84).
     */
    @Transactional
    public Estado entregarGabarito(Long id, GabaritoRequest pedido) {
        var prova = buscar(id);
        if (pedido.gabarito() == null) {
            throw new ValidationException("O gabarito não veio.", "GABARITO_OBRIGATORIO", "gabarito");
        }
        var questoes = questaoRepository.findByProvaIdOrderByNumeroAsc(id);
        var status = prova.getStatus();
        var esperando = (status == StatusProva.LENDO || status == StatusProva.AGUARDANDO_GABARITO) && !questoes.isEmpty();
        var definitivo = pedido.gabarito().situacao() == SituacaoGabarito.DEFINITIVO
            && EnumSet.of(StatusProva.CONFERIR, StatusProva.JULGANDO, StatusProva.PRONTA).contains(status);
        if (!esperando && !definitivo) {
            throw new ConflictException("A prova está em " + status + "; não aceita este gabarito agora.", "ESTADO_INVALIDO");
        }
        if (pedido.modelo() != null) prova.setModelo(pedido.modelo());
        aplicarGabarito(prova, questoes, pedido.gabarito());
        prova.setErro(null);
        questaoRepository.flush();
        // D-84: se o definitivo mudou ou anulou uma resposta, a nota da tentativa que conta se recalcula.
        resolucaoService.recalcularNotaQueConta(prova);

        if (esperando) {
            prova.setStatus(StatusProva.CONFERIR);
        } else if (status == StatusProva.PRONTA && !questaoRepository.idsAJulgar(id).isEmpty()) {
            prova.setStatus(StatusProva.JULGANDO);
        }
        return new Estado(prova.getId(), prova.getStatus());
    }

    // ------------------------------------------------------------ julgamento

    /** As questões a julgar (D-79) e, de cada uma, o assunto e seus segmentos ativos. */
    @Transactional(readOnly = true)
    public Julgamento paraJulgar(Long id) {
        var prova = buscar(id);
        exigir(prova, StatusProva.JULGANDO);
        var aJulgar = new HashSet<>(questaoRepository.idsAJulgar(id));
        var questoes = questaoRepository.findByProvaIdOrderByNumeroAsc(id).stream()
            .filter(q -> aJulgar.contains(q.getId()))
            .map(q -> paraJulgar(q))
            .toList();
        return new Julgamento(prova.getId(), prova.getFormato(), questoes);
    }

    private QuestaoParaJulgar paraJulgar(Questao q) {
        var assunto = q.getAssunto();
        var segmentos = segmentoRepository.findByAssuntoIdAndArquivadoEmIsNullOrderByOrdemAsc(assunto.getId()).stream()
            .map(s -> new SegmentoParaJulgar(s.getId(), s.getArquivo(), s.getOrdem(), s.getPaginaInicial(), s.getPaginaFinal()))
            .toList();
        return new QuestaoParaJulgar(q.getId(), q.getNumero(), q.getEnunciado(), q.getTextoBase(),
            q.getAlternativas().stream().map(a -> new AlternativaLida(a.getLetra(), a.getTexto())).toList(),
            q.getRespostaGabarito(),
            new AssuntoParaJulgar(assunto.getId(), assunto.getChaveExterna(), assunto.getNome(),
                assunto.getDisciplina().getNome(), segmentos));
    }

    /**
     * Grava os vereditos (D-73: sempre linha nova). RESPONDIVEL exige a página que sustenta a
     * alternativa do gabarito, num segmento ativo do assunto da questão; citação que não passa
     * rebaixa o item para NAO_RESPONDIVEL e volta em {@code rebaixados} (D-68). Sem mais nada a
     * julgar, a prova fica PRONTA.
     */
    @Transactional
    public VereditosResponse registrarVereditos(Long id, VereditosRequest pedido) {
        var prova = buscar(id);
        exigir(prova, StatusProva.JULGANDO);
        var rebaixados = new ArrayList<ItemRecusado>();
        var recusados = new ArrayList<ItemRecusado>();
        var gravados = 0;
        if (pedido.modelo() != null) prova.setModelo(pedido.modelo());

        for (var item : pedido.itens() == null ? List.<ItemVeredito>of() : pedido.itens()) {
            var questao = item.numero() == null ? null : questaoRepository.findByProvaIdAndNumero(id, item.numero()).orElse(null);
            if (questao == null) {
                recusados.add(new ItemRecusado(item.numero(), "Questão inexistente nesta prova."));
                continue;
            }
            if (!questao.avaliavel() || questao.getAssunto() == null || questao.getRespostaGabarito() == null) {
                recusados.add(new ItemRecusado(item.numero(), "Questão não avaliável ou sem assunto atribuído (D-70)."));
                continue;
            }
            if (item.veredito() == null) {
                recusados.add(new ItemRecusado(item.numero(), "Veredito ausente."));
                continue;
            }
            var segmento = segmentoCitado(questao, item);
            var citacaoValida = segmento != null;
            var veredito = item.veredito();
            var falta = item.falta();
            if (veredito == VereditoCobertura.RESPONDIVEL && !citacaoValida) {
                veredito = VereditoCobertura.NAO_RESPONDIVEL;
                var razao = "Citação de sustentação inválida: sem segmento ativo do assunto ou página fora dele (D-68).";
                falta = falta == null || falta.isBlank() ? razao : falta + " [" + razao + "]";
                rebaixados.add(new ItemRecusado(item.numero(), razao));
            }
            var sustenta = veredito == VereditoCobertura.NAO_RESPONDIVEL ? null : segmento;
            vereditoRepository.save(new VereditoQuestao(questao, veredito, sustenta,
                sustenta == null ? null : item.paginaSegmento(), falta, pedido.modelo()));
            gravados++;
        }
        vereditoRepository.flush();

        if (questaoRepository.idsAJulgar(id).isEmpty()) {
            prova.setStatus(StatusProva.PRONTA);
        }
        return new VereditosResponse(gravados, rebaixados, recusados, prova.getStatus());
    }

    /** Segmento ativo do assunto da questão que contém a página citada; nulo se a citação não passa (D-68). */
    private Segmento segmentoCitado(Questao questao, ItemVeredito item) {
        if (item.segmentoId() == null || item.paginaSegmento() == null || item.paginaSegmento() < 1) return null;
        var segmento = segmentoRepository.findById(item.segmentoId()).orElse(null);
        if (segmento == null || segmento.getArquivadoEm() != null) return null;
        if (!segmento.getAssunto().getId().equals(questao.getAssunto().getId())) return null;
        if (segmento.getPaginaInicial() != null && segmento.getPaginaFinal() != null) {
            var paginas = segmento.getPaginaFinal() - segmento.getPaginaInicial() + 1;
            if (item.paginaSegmento() > paginas) return null;
        }
        return segmento;
    }

    @Transactional
    public Estado falhar(Long id, FalhaRequest pedido) {
        var prova = buscar(id);
        if (pedido.etapa() != StatusProva.LENDO && pedido.etapa() != StatusProva.JULGANDO) {
            throw new ValidationException("A etapa da falha é LENDO ou JULGANDO.", "ETAPA_INVALIDA", "etapa");
        }
        prova.setStatus(StatusProva.FALHOU);
        prova.setEtapaFalha(pedido.etapa());
        var erro = pedido.erro() == null ? "" : pedido.erro();
        prova.setErro(erro.length() > ERRO_MAX ? erro.substring(0, ERRO_MAX) : erro);
        return new Estado(prova.getId(), prova.getStatus());
    }

    // ------------------------------------------------------------ internos

    private static String limitar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        return texto.length() > ERRO_MAX ? texto.substring(0, ERRO_MAX) : texto;
    }

    private Prova buscar(Long id) {
        return provaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Prova não encontrada.", "PROVA_INEXISTENTE"));
    }

    private void exigir(Prova prova, StatusProva esperado) {
        if (prova.getStatus() != esperado) {
            throw new ConflictException("A prova está em " + prova.getStatus() + "; esta rota exige " + esperado + ".", "ESTADO_INVALIDO");
        }
    }

    private record Cobertura(Assunto assunto, String disciplina) {}

    /** Assuntos ativos da cobertura do concurso da prova, por chave externa (D-74, D-77). */
    private Map<String, Cobertura> coberturaDoAlvo(Prova prova) {
        var mapa = new HashMap<String, Cobertura>();
        for (var c : coberturaRepository.listarDoConcurso(prova.getConcurso().getId())) {
            var assunto = c.getAssunto();
            if (assunto.isAtivo() && assunto.getChaveExterna() != null) {
                mapa.put(assunto.getChaveExterna(), new Cobertura(assunto, c.getDisciplina().getNome()));
            }
        }
        return mapa;
    }

    private Questao criarQuestao(Prova prova, FormatoBanca formato, QuestaoLida lida, Map<String, Cobertura> cobertura) {
        var questao = new Questao(prova, lida.numero());
        var semTexto = lida.enunciado() == null || lida.enunciado().isBlank();
        var motivo = lida.motivoNaoAvaliavel();
        if (motivo == MotivoNaoAvaliavel.ANULADA || motivo == MotivoNaoAvaliavel.SEM_GABARITO) {
            throw new ValidationException("Anulada e sem gabarito são decididas pelo sistema (D-70).", "MOTIVO_INVALIDO", "motivoNaoAvaliavel");
        }
        questao.setEnunciado(semTexto ? null : lida.enunciado());
        questao.setMotivoNaoAvaliavel(semTexto ? MotivoNaoAvaliavel.SEM_TEXTO : motivo);
        questao.setTextoBase(lida.textoBase());
        questao.setCabecalhoDisciplina(lida.cabecalhoDisciplina());
        questao.setLidaPorOcr(Boolean.TRUE.equals(lida.lidaPorOcr()));
        questao.setPaginaOrigem(lida.paginaOrigem());

        if (formato == FormatoBanca.MULTIPLA_ESCOLHA && lida.alternativas() != null) {
            var letras = new HashSet<String>();
            for (var alternativa : lida.alternativas()) {
                var letra = letraValida(alternativa.letra(), LETRAS, "alternativas");
                if (!letras.add(letra)) {
                    throw new ValidationException("Alternativa repetida na questão " + lida.numero() + ".", "ALTERNATIVA_REPETIDA", "alternativas");
                }
                questao.adicionar(letra, alternativa.texto());
            }
        }

        var chave = lida.assuntoChaveExterna();
        var dona = chave == null || chave.isBlank() ? null : cobertura.get(chave);
        if (dona != null) {
            questao.setAssunto(dona.assunto());
            questao.setAtribuicao(Boolean.TRUE.equals(lida.baixaConfianca()) ? AtribuicaoQuestao.REVISAR : AtribuicaoQuestao.AUTOMATICA);
        } else if (questao.getMotivoNaoAvaliavel() == MotivoNaoAvaliavel.SEM_TEXTO && (chave == null || chave.isBlank())) {
            // Sem texto não há o que atribuir: não é "fora do edital", é ilegível.
            questao.setAtribuicao(AtribuicaoQuestao.PENDENTE);
        } else {
            // D-74 — "nenhum", ou um assunto que não é da cobertura do alvo: descartado.
            questao.setAtribuicao(AtribuicaoQuestao.FORA_DO_EDITAL);
        }
        return questao;
    }

    /**
     * Aplica o gabarito às questões (D-70, D-82). Sem gabarito, toda questão avaliável fica
     * SEM_GABARITO. Os motivos da leitura (SEM_TEXTO, OCR_DUVIDOSO) valem mais que os do
     * gabarito: sem texto utilizável a questão sai do denominador de qualquer jeito.
     */
    private void aplicarGabarito(Prova prova, List<Questao> questoes, GabaritoLido gabarito) {
        var formato = prova.getFormato();
        Map<Integer, RespostaGabarito> porNumero = new HashMap<>();
        if (gabarito != null) {
            var respostas = gabarito.respostas() == null ? List.<RespostaGabarito>of() : gabarito.respostas();
            for (var r : respostas) {
                if (r.numero() == null || porNumero.put(r.numero(), r) != null) {
                    throw new ValidationException("Gabarito com numeração inválida ou repetida.", "GABARITO_NAO_CASA", "gabarito");
                }
            }
            var daProva = new HashSet<Integer>();
            questoes.forEach(q -> daProva.add(q.getNumero()));
            if (!daProva.equals(porNumero.keySet())) {
                throw new ValidationException("O gabarito traz " + porNumero.size() + " respostas e a prova " + daProva.size()
                    + " questões, ou a numeração não coincide (D-82).", "GABARITO_NAO_CASA", "gabarito");
            }
            if (gabarito.situacao() == null) {
                throw new ValidationException("Informe se o gabarito é preliminar ou definitivo.", "SITUACAO_OBRIGATORIA", "gabarito");
            }
        }

        for (var q : questoes) {
            var doGabarito = q.getMotivoNaoAvaliavel() == null
                || q.getMotivoNaoAvaliavel() == MotivoNaoAvaliavel.ANULADA
                || q.getMotivoNaoAvaliavel() == MotivoNaoAvaliavel.SEM_GABARITO;
            if (gabarito == null) {
                q.setRespostaGabarito(null);
                if (doGabarito) q.setMotivoNaoAvaliavel(MotivoNaoAvaliavel.SEM_GABARITO);
                continue;
            }
            var r = porNumero.get(q.getNumero());
            var bruta = r.resposta() == null ? "" : r.resposta().strip().toUpperCase(Locale.ROOT);
            var anulada = Boolean.TRUE.equals(r.anulada()) || ANULADA.contains(bruta);
            if (anulada) {
                q.setRespostaGabarito(null);
                if (doGabarito) q.setMotivoNaoAvaliavel(MotivoNaoAvaliavel.ANULADA);
                continue;
            }
            var permitidas = formato == FormatoBanca.CERTO_ERRADO ? CERTO_ERRADO : LETRAS;
            q.setRespostaGabarito(letraValida(bruta, permitidas, "gabarito"));
            if (doGabarito) q.setMotivoNaoAvaliavel(null);
        }
        prova.setGabaritoSituacao(gabarito == null ? null : gabarito.situacao());
        prova.setGabaritoRecorte(gabarito == null ? null : gabarito.recorte());
        prova.setQuestoesEsperadas(gabarito == null ? null : porNumero.size());
    }

    private String letraValida(String letra, Set<String> permitidas, String campo) {
        var normal = letra == null ? "" : letra.strip().toUpperCase(Locale.ROOT);
        if (!permitidas.contains(normal)) {
            throw new ValidationException("Letra inválida: '" + letra + "'.", "LETRA_INVALIDA", campo);
        }
        return normal;
    }
}
