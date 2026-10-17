package br.com.estudos.edital;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.concurso.Concurso;
import br.com.estudos.concurso.ConcursoService;
import br.com.estudos.concurso.ItemCobertura;
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.enums.OrigemConcurso;
import br.com.estudos.shared.enums.StatusAnaliseEdital;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.IndisponivelException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Análise de edital por IA (01_DOMINIO §3.9, ADR-043, docs/SPRINT-18-CONCURSOS.md §4).
 * Duas etapas em segundo plano, uma análise por vez; a chamada à IA nunca
 * acontece dentro de transação. A confirmação é a única escrita no cadastro
 * (D-62).
 */
@Service
public class AnaliseEditalService {

    private static final Logger log = LoggerFactory.getLogger(AnaliseEditalService.class);
    private static final int MAX_NOME_ASSUNTO = 250;
    private static final int MAX_NOME_DISCIPLINA = 150;

    private final AnaliseEditalRepository repository;
    private final ExtratorTextoPdf extrator;
    private final ClienteOpenRouter cliente;
    private final ConfiguracaoIa configuracao;
    private final JsonMapper jsonMapper;
    private final AssuntoRepository assuntoRepository;
    private final AssuntoService assuntoService;
    private final DisciplinaRepository disciplinaRepository;
    private final DisciplinaService disciplinaService;
    private final ConcursoService concursoService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(tarefa -> {
        var thread = new Thread(tarefa, "analise-edital");
        thread.setDaemon(true);
        return thread;
    });

    public AnaliseEditalService(
            AnaliseEditalRepository repository,
            ExtratorTextoPdf extrator,
            ClienteOpenRouter cliente,
            ConfiguracaoIa configuracao,
            JsonMapper jsonMapper,
            AssuntoRepository assuntoRepository,
            AssuntoService assuntoService,
            DisciplinaRepository disciplinaRepository,
            DisciplinaService disciplinaService,
            ConcursoService concursoService) {
        this.repository = repository;
        this.extrator = extrator;
        this.cliente = cliente;
        this.configuracao = configuracao;
        this.jsonMapper = jsonMapper;
        this.assuntoRepository = assuntoRepository;
        this.assuntoService = assuntoService;
        this.disciplinaRepository = disciplinaRepository;
        this.disciplinaService = disciplinaService;
        this.concursoService = concursoService;
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }

    public IaStatusResponse statusIa() {
        return configuracao.disponivel()
            ? new IaStatusResponse(true, null, configuracao.modelo())
            : new IaStatusResponse(false, "Cadastre a chave do OpenRouter em Ajustes → IA para ler editais com IA.", configuracao.modelo());
    }

    /** Recebe o PDF, extrai o texto e começa a etapa 1. */
    public AnaliseEdital iniciar(MultipartFile arquivo) {
        if (!configuracao.disponivel()) {
            throw new IndisponivelException("Análise por IA indisponível: " + statusIa().motivo(), "IA_INDISPONIVEL");
        }
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Não foi possível ler o arquivo.", "ARQUIVO_ILEGIVEL", "arquivo");
        }
        var texto = extrator.extrair(bytes);
        var nome = arquivo.getOriginalFilename() == null ? "edital.pdf" : arquivo.getOriginalFilename();
        var analise = repository.save(new AnaliseEdital(cortar(nome, 250), texto, configuracao.modelo()));
        agendar(analise.getId(), StatusAnaliseEdital.LENDO_EDITAL);
        return buscar(analise.getId());
    }

    @Transactional(readOnly = true)
    public AnaliseEdital buscar(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Análise não encontrada.", "ANALISE_INEXISTENTE"));
    }

    @Transactional(readOnly = true)
    public List<AnaliseEdital> recentes() {
        return repository.findTop10ByOrderByCriadoEmDescIdDesc();
    }

    /** Escolhe o cargo e começa a etapa 2. */
    public AnaliseEdital escolherCargo(Long id, String cargo) {
        var analise = buscar(id);
        exigir(analise, StatusAnaliseEdital.ESCOLHER_CARGO);
        if (cargo == null || !cargosDe(analise).contains(cargo)) {
            throw new ValidationException("Escolha um dos cargos encontrados no edital.", "CARGO_INVALIDO", "cargo");
        }
        analise.setCargoEscolhido(cargo);
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        repository.save(analise);
        agendar(id, StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        return buscar(id);
    }

    /** Repete a etapa que falhou, com o texto já guardado. */
    public AnaliseEdital tentarDeNovo(Long id) {
        var analise = buscar(id);
        exigir(analise, StatusAnaliseEdital.FALHOU);
        if (!configuracao.disponivel()) {
            throw new IndisponivelException("Análise por IA indisponível: " + statusIa().motivo(), "IA_INDISPONIVEL");
        }
        var etapa = analise.getEtapaFalha() == null ? StatusAnaliseEdital.LENDO_EDITAL : analise.getEtapaFalha();
        analise.setStatus(etapa);
        analise.setEtapaFalha(null);
        analise.setErro(null);
        repository.save(analise);
        agendar(id, etapa);
        return buscar(id);
    }

    /** Reiniciar o servidor no meio de uma etapa deixa a análise em falha, com "tentar de novo" (§4.1). */
    @EventListener(ApplicationReadyEvent.class)
    public void marcarInterrompidas() {
        for (var analise : repository.findByStatusIn(Set.of(StatusAnaliseEdital.LENDO_EDITAL, StatusAnaliseEdital.SEPARANDO_ASSUNTOS))) {
            falhar(analise, analise.getStatus(), "Interrompida por reinício do servidor. Tente de novo.");
        }
    }

    private void agendar(Long id, StatusAnaliseEdital etapa) {
        Runnable tarefa = etapa == StatusAnaliseEdital.LENDO_EDITAL ? () -> etapaCargos(id) : () -> etapaAssuntos(id);
        if (configuracao.sincrono()) {
            tarefa.run();
        } else {
            executor.submit(tarefa);
        }
    }

    // ---------------------------------------------------------------- etapa 1

    void etapaCargos(Long id) {
        var analise = repository.findById(id).orElse(null);
        if (analise == null) return;
        try {
            var json = lerJson(cliente.completar(PromptsEdital.SISTEMA, PromptsEdital.cargos(analise.getTexto())));
            var cargos = new ArrayList<String>();
            for (var cargo : json.path("cargos")) {
                var nome = texto(cargo);
                if (nome != null && !cargos.contains(nome)) cargos.add(cortar(nome, 250));
            }
            if (cargos.isEmpty()) throw new FalhaIaException("A IA não encontrou nenhum cargo no edital.");
            analise.setConcursoNome(cortar(texto(json.get("concurso")), 250));
            analise.setBanca(cortar(texto(json.get("banca")), 150));
            analise.setDataProva(data(texto(json.get("dataProva"))));
            analise.setCargos(jsonMapper.writeValueAsString(cargos));
            analise.setStatus(StatusAnaliseEdital.ESCOLHER_CARGO);
            repository.save(analise);
        } catch (RuntimeException e) {
            falhar(analise, StatusAnaliseEdital.LENDO_EDITAL, mensagem(e));
        }
    }

    // ---------------------------------------------------------------- etapa 2

    void etapaAssuntos(Long id) {
        var analise = repository.findById(id).orElse(null);
        if (analise == null) return;
        try {
            var disciplinas = disciplinaRepository.findByAtivoTrue();
            var assuntos = assuntoRepository.listarAtivosDeDisciplinasAtivas();
            var json = lerJson(cliente.completar(PromptsEdital.SISTEMA,
                PromptsEdital.assuntos(analise.getTexto(), analise.getCargoEscolhido(), disciplinas, assuntos)));
            var proposta = montarProposta(json, disciplinas, assuntos);
            if (proposta.disciplinas().isEmpty()) {
                throw new FalhaIaException("A IA não encontrou conteúdo programático para este cargo.");
            }
            analise.setProposta(jsonMapper.writeValueAsString(proposta));
            analise.setStatus(StatusAnaliseEdital.PRONTA);
            repository.save(analise);
        } catch (RuntimeException e) {
            falhar(analise, StatusAnaliseEdital.SEPARANDO_ASSUNTOS, mensagem(e));
        }
    }

    /**
     * Da resposta da IA à proposta: peso por regra (D-61), correspondência só
     * com assunto ativo e uma vez cada (D-63), nomes limpos e sem repetição.
     */
    PropostaEdital montarProposta(JsonNode json, List<Disciplina> disciplinas, List<Assunto> assuntos) {
        var disciplinasPorId = disciplinas.stream().collect(Collectors.toMap(Disciplina::getId, Function.identity()));
        var disciplinasPorNome = disciplinas.stream()
            .collect(Collectors.toMap(d -> normalizar(d.getNome()), Function.identity(), (a, b) -> a));
        var assuntosPorId = assuntos.stream().collect(Collectors.toMap(Assunto::getId, Function.identity()));
        var correspondidos = new HashSet<Long>();

        record Extraida(String nome, Integer questoes, Long disciplinaId, List<PropostaEdital.Assunto> assuntos) {}
        var extraidas = new ArrayList<Extraida>();
        var nomesDisciplina = new HashSet<String>();
        for (var no : json.path("disciplinas")) {
            var nome = cortar(texto(no.get("nome")), MAX_NOME_DISCIPLINA);
            if (nome == null || !nomesDisciplina.add(normalizar(nome))) continue;
            var disciplinaId = inteiroLongo(no.get("disciplinaExistenteId"));
            if (disciplinaId != null && !disciplinasPorId.containsKey(disciplinaId)) disciplinaId = null;
            if (disciplinaId == null && disciplinasPorNome.containsKey(normalizar(nome))) {
                disciplinaId = disciplinasPorNome.get(normalizar(nome)).getId();
            }
            var nomesAssunto = new HashSet<String>();
            var propostos = new ArrayList<PropostaEdital.Assunto>();
            for (var noAssunto : no.path("assuntos")) {
                var nomeAssunto = cortar(texto(noAssunto.get("nome")), MAX_NOME_ASSUNTO);
                if (nomeAssunto == null || !nomesAssunto.add(normalizar(nomeAssunto))) continue;
                var existente = inteiroLongo(noAssunto.get("assuntoExistenteId"));
                // D-63: só assunto ativo existente, e cada um uma vez só.
                if (existente != null && (!assuntosPorId.containsKey(existente) || !correspondidos.add(existente))) {
                    existente = null;
                }
                propostos.add(new PropostaEdital.Assunto(nomeAssunto, existente));
            }
            if (!propostos.isEmpty()) {
                extraidas.add(new Extraida(nome, inteiro(no.get("questoes")), disciplinaId, propostos));
            }
        }
        var pesos = PesoPorQuestoes.calcular(extraidas.stream().map(Extraida::questoes).toList());
        var resultado = new ArrayList<PropostaEdital.Disciplina>();
        for (int i = 0; i < extraidas.size(); i++) {
            var e = extraidas.get(i);
            resultado.add(new PropostaEdital.Disciplina(
                e.nome(), e.questoes(), pesos.get(i).peso(), pesos.get(i).determinado(), e.disciplinaId(), e.assuntos()));
        }
        return new PropostaEdital(resultado);
    }

    // ---------------------------------------------------------------- confirmação

    /**
     * D-62 — a única escrita no cadastro. Cria o que é novo, aplica a
     * cobertura (D-60) e ativa o concurso. Tudo numa transação: falhou, nada
     * fica. D-64: o concurso nasce com identificador e todo assunto coberto
     * sem chave externa ganha uma — o material preparado depois reencontra os
     * dois por elas, nunca pelo nome (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §2).
     */
    @Transactional
    public Concurso confirmar(Long id, ConfirmacaoEditalRequest pedido) {
        var analise = buscar(id);
        exigir(analise, StatusAnaliseEdital.PRONTA);
        var nomeConcurso = cortar(pedido.nome(), 250);
        if (nomeConcurso == null) {
            throw new ValidationException("Informe o nome do concurso.", "NOME_OBRIGATORIO", "nome");
        }
        var ativos = assuntoRepository.listarAtivosDeDisciplinasAtivas().stream()
            .collect(Collectors.toMap(Assunto::getId, Function.identity()));
        var todasDisciplinas = new ArrayList<>(disciplinaRepository.findAll());
        var usados = new HashSet<Long>();
        var itens = new ArrayList<ItemCobertura>();
        int provisoria = 0;

        for (var pedidoDisciplina : pedido.disciplinas() == null ? List.<ConfirmacaoEditalRequest.Disciplina>of() : pedido.disciplinas()) {
            var nomesAssunto = pedidoDisciplina.assuntos() == null ? List.<ConfirmacaoEditalRequest.Assunto>of()
                : pedidoDisciplina.assuntos().stream().filter(a -> cortar(a.nome(), MAX_NOME_ASSUNTO) != null).toList();
            var nomeDisciplina = cortar(pedidoDisciplina.nome(), MAX_NOME_DISCIPLINA);
            if (nomesAssunto.isEmpty() || nomeDisciplina == null) continue;
            var peso = pedidoDisciplina.peso() == null ? TipoPeso.MEDIO : pedidoDisciplina.peso();
            var disciplina = resolverDisciplina(pedidoDisciplina.disciplinaExistenteId(), nomeDisciplina, peso, todasDisciplinas);
            disciplina.setPeso(peso);

            int ordem = 0;
            for (var pedidoAssunto : nomesAssunto) {
                var nome = cortar(pedidoAssunto.nome(), MAX_NOME_ASSUNTO);
                // D-63: a correspondência do corpo é revalidada aqui.
                var assunto = pedidoAssunto.assuntoExistenteId() == null ? null : ativos.get(pedidoAssunto.assuntoExistenteId());
                if (assunto != null && !usados.add(assunto.getId())) assunto = null;
                if (assunto == null) assunto = homonimo(disciplina, nome, usados);
                if (assunto == null) {
                    assunto = assuntoService.criar(new AssuntoRequest(
                        disciplina.getId(), nome, peso, (short) 3, -1_000_000 - (++provisoria), null));
                    usados.add(assunto.getId());
                }
                itens.add(new ItemCobertura(assunto, disciplina, peso, ++ordem));
            }
        }
        if (itens.isEmpty()) {
            throw new ValidationException("A proposta ficou sem nenhum assunto.", "PROPOSTA_VAZIA", "disciplinas");
        }

        for (var item : itens) {
            if (item.assunto().getChaveExterna() == null) {
                item.assunto().setChaveExterna(UUID.randomUUID().toString()); // D-64
            }
        }

        var concurso = new Concurso(nomeConcurso, OrigemConcurso.ANALISE_IA, concursoService.novoSlug(nomeConcurso));
        concurso.setCargo(cortar(pedido.cargo(), 250));
        concurso.setBanca(cortar(pedido.banca(), 150));
        concurso.setDataProva(pedido.dataProva());
        var salvo = concursoService.ativarComCobertura(concurso, itens);

        analise.setStatus(StatusAnaliseEdital.CONFIRMADA);
        analise.setConcursoId(salvo.getId());
        repository.save(analise);
        return salvo;
    }

    /** Disciplina pelo id (ativa), senão pelo nome sem acento e sem caixa (D-47: único mesmo arquivada), senão nova. */
    private Disciplina resolverDisciplina(Long idExistente, String nome, TipoPeso peso, List<Disciplina> todas) {
        if (idExistente != null) {
            var porId = todas.stream().filter(d -> d.getId().equals(idExistente) && d.isAtivo()).findFirst();
            if (porId.isPresent()) return porId.get();
        }
        var alvo = normalizar(nome);
        var porNome = todas.stream().filter(d -> normalizar(d.getNome()).equals(alvo)).findFirst();
        if (porNome.isPresent()) {
            if (!porNome.get().isAtivo()) disciplinaService.reativar(porNome.get().getId());
            return porNome.get();
        }
        var nova = disciplinaService.criar(new DisciplinaRequest(nome, peso));
        todas.add(nova);
        return nova;
    }

    /**
     * Assunto da disciplina com o mesmo nome (sem acento, sem caixa) — o
     * índice de nome valeria contra criar outro. Arquivado volta a ativo: o
     * edital novo o cobre de novo; antes, ordem provisória, porque a antiga
     * pode ter sido ocupada enquanto ele estava arquivado (D-48).
     */
    private Assunto homonimo(Disciplina disciplina, String nome, Set<Long> usados) {
        if (disciplina.getId() == null) return null;
        var alvo = normalizar(nome);
        var achado = assuntoRepository.findByDisciplinaId(disciplina.getId()).stream()
            .filter(a -> normalizar(a.getNome()).equals(alvo) && !usados.contains(a.getId()))
            .findFirst()
            .orElse(null);
        if (achado == null) return null;
        if (!achado.isAtivo()) {
            achado.setOrdem(Math.toIntExact(-achado.getId()));
            assuntoRepository.saveAndFlush(achado);
            assuntoService.reativar(achado.getId());
        }
        usados.add(achado.getId());
        return achado;
    }

    // ---------------------------------------------------------------- resposta

    @Transactional(readOnly = true)
    public AnaliseEditalResponse resposta(AnaliseEdital analise) {
        List<AnaliseEditalResponse.Disciplina> proposta = null;
        AnaliseEditalResponse.Resumo resumo = null;
        if (analise.getProposta() != null && analise.getStatus() == StatusAnaliseEdital.PRONTA) {
            var lida = jsonMapper.readValue(analise.getProposta(), PropostaEdital.class);
            var ativos = assuntoRepository.listarAtivosDeDisciplinasAtivas();
            var ativosPorId = ativos.stream().collect(Collectors.toMap(Assunto::getId, Function.identity()));
            var disciplinasPorId = new HashMap<Long, String>();
            disciplinaRepository.findAll().forEach(d -> disciplinasPorId.put(d.getId(), d.getNome()));
            int novos = 0;
            var emComum = new HashSet<Long>();
            proposta = new ArrayList<>();
            for (var d : lida.disciplinas()) {
                var assuntos = new ArrayList<AnaliseEditalResponse.Assunto>();
                for (var a : d.assuntos()) {
                    // Arquivado depois da análise: a correspondência deixa de valer (D-63).
                    var existente = a.assuntoExistenteId() == null ? null : ativosPorId.get(a.assuntoExistenteId());
                    if (existente == null) novos++; else emComum.add(existente.getId());
                    assuntos.add(new AnaliseEditalResponse.Assunto(
                        a.nome(),
                        existente == null ? null : existente.getId(),
                        existente == null ? null : existente.getNome(),
                        existente == null ? null : existente.getDisciplina().getNome()));
                }
                proposta.add(new AnaliseEditalResponse.Disciplina(
                    d.nome(), d.questoes(), d.peso(), d.pesoDeterminado(), d.disciplinaExistenteId(),
                    d.disciplinaExistenteId() == null ? null : disciplinasPorId.get(d.disciplinaExistenteId()),
                    assuntos));
            }
            resumo = new AnaliseEditalResponse.Resumo(novos, emComum.size(), ativos.size() - emComum.size());
        }
        return new AnaliseEditalResponse(
            analise.getId(), analise.getNomeArquivo(), analise.getStatus(), analise.getEtapaFalha(), analise.getErro(),
            analise.getModelo(), analise.getConcursoNome(), analise.getBanca(), analise.getDataProva(),
            cargosDe(analise), analise.getCargoEscolhido(), proposta, resumo, analise.getConcursoId(),
            analise.getCriadoEm(), analise.getAtualizadoEm());
    }

    // ---------------------------------------------------------------- apoio

    private List<String> cargosDe(AnaliseEdital analise) {
        if (analise.getCargos() == null) return List.of();
        return List.of(jsonMapper.readValue(analise.getCargos(), String[].class));
    }

    private void exigir(AnaliseEdital analise, StatusAnaliseEdital esperado) {
        if (analise.getStatus() != esperado) {
            throw new BusinessRuleException(
                "Esta ação não cabe agora: a análise está em " + analise.getStatus() + ".", "ETAPA_INVALIDA");
        }
    }

    private void falhar(AnaliseEdital analise, StatusAnaliseEdital etapa, String mensagem) {
        log.warn("Análise de edital {} falhou na etapa {}: {}", analise.getId(), etapa, mensagem);
        analise.setStatus(StatusAnaliseEdital.FALHOU);
        analise.setEtapaFalha(etapa);
        analise.setErro(mensagem);
        repository.save(analise);
    }

    private String mensagem(RuntimeException e) {
        if (e instanceof FalhaIaException || e instanceof IndisponivelException) return e.getMessage();
        if (e instanceof JacksonException) return "A IA respondeu fora do formato esperado. Tente de novo.";
        log.error("Falha inesperada na análise de edital", e);
        return "Falha inesperada na análise (" + e.getClass().getSimpleName() + "). Tente de novo.";
    }

    /** Tira cercas de código e pega do primeiro `{` ao último `}` (§4.3). */
    private JsonNode lerJson(String conteudo) {
        int inicio = conteudo.indexOf('{');
        int fim = conteudo.lastIndexOf('}');
        if (inicio < 0 || fim <= inicio) throw new FalhaIaException("A IA respondeu fora do formato esperado. Tente de novo.");
        return jsonMapper.readTree(conteudo.substring(inicio, fim + 1));
    }

    private static String texto(JsonNode no) {
        if (no == null || no.isNull() || no.isMissingNode()) return null;
        var valor = no.asString().trim();
        return valor.isEmpty() || valor.equalsIgnoreCase("null") ? null : valor;
    }

    private static Integer inteiro(JsonNode no) {
        var valor = inteiroLongo(no);
        return valor == null ? null : Math.toIntExact(Math.min(valor, 10_000));
    }

    private static Long inteiroLongo(JsonNode no) {
        if (no == null || no.isNull()) return null;
        if (no.isNumber()) return no.asLong();
        var t = texto(no);
        if (t == null) return null;
        try {
            return Long.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate data(String texto) {
        if (texto == null) return null;
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String cortar(String texto, int maximo) {
        if (texto == null) return null;
        var limpo = texto.strip().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) return null;
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo);
    }

    static String normalizar(String texto) {
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase()
            .trim();
    }
}
