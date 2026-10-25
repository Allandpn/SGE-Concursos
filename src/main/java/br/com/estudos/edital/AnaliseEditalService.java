package br.com.estudos.edital;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import br.com.estudos.shared.enums.StatusAnaliseEdital;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.IndisponivelException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Análise de edital (01_DOMINIO §3.9, ADR-043 e ADR-047): o SGE lê só o cabeçalho (nome, banca, data da
 * prova e cargos), em segundo plano e uma análise por vez; a chamada à IA nunca acontece dentro de
 * transação. A separação em disciplinas, assuntos e partes é do planejamento e chega como conferência
 * (docs/SPRINT-25-CONFERENCIA-EDITAL.md).
 */
@Service
public class AnaliseEditalService {

    private static final Logger log = LoggerFactory.getLogger(AnaliseEditalService.class);

    private final AnaliseEditalRepository repository;
    private final ExtratorTextoPdf extrator;
    private final ServicoIa cliente;
    private final ConfiguracaoIa configuracao;
    private final JsonMapper jsonMapper;
    private final EditalArquivos arquivos;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(tarefa -> {
        var thread = new Thread(tarefa, "analise-edital");
        thread.setDaemon(true);
        return thread;
    });

    public AnaliseEditalService(
            AnaliseEditalRepository repository,
            ExtratorTextoPdf extrator,
            ServicoIa cliente,
            ConfiguracaoIa configuracao,
            JsonMapper jsonMapper,
            EditalArquivos arquivos) {
        this.repository = repository;
        this.extrator = extrator;
        this.cliente = cliente;
        this.configuracao = configuracao;
        this.jsonMapper = jsonMapper;
        this.arquivos = arquivos;
    }

    @PreDestroy
    void encerrar() {
        executor.shutdownNow();
    }

    public IaStatusResponse statusIa() {
        return configuracao.disponivel()
            ? new IaStatusResponse(true, null, configuracao.modeloEmUso())
            : new IaStatusResponse(false, configuracao.provedor() == ProvedorIa.CLAUDE
                ? "Cadastre o token da assinatura do Claude em Ajustes → IA para ler editais com IA."
                : "Cadastre a chave do OpenRouter em Ajustes → IA para ler editais com IA.", configuracao.modeloEmUso());
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
        var analise = repository.save(new AnaliseEdital(cortar(nome, 250), texto, configuracao.modeloEmUso()));
        analise.setPdfArquivo(arquivos.salvarPdf(analise.getId(), bytes));
        repository.save(analise);
        agendar(analise.getId());
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

    /** Escolhe o cargo; o planejamento pega a análise em seguida (GET /internal/editais/pendentes). */
    public AnaliseEdital escolherCargo(Long id, String cargo) {
        var analise = buscar(id);
        exigir(analise, StatusAnaliseEdital.ESCOLHER_CARGO);
        if (cargo == null || !cargosDe(analise).contains(cargo)) {
            throw new ValidationException("Escolha um dos cargos encontrados no edital.", "CARGO_INVALIDO", "cargo");
        }
        analise.setCargoEscolhido(cargo);
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS); // quem separa é o planejamento (ADR-047)
        repository.save(analise);
        return buscar(id);
    }

    /**
     * Repete a etapa que falhou: a leitura do cabeçalho roda de novo aqui; qualquer etapa do planejamento
     * volta a "pendente" para o worker pegar de novo.
     */
    public AnaliseEdital tentarDeNovo(Long id) {
        var analise = buscar(id);
        exigir(analise, StatusAnaliseEdital.FALHOU);
        var cabecalho = analise.getEtapaFalha() == null || StatusAnaliseEdital.LENDO_EDITAL.name().equals(analise.getEtapaFalha());
        if (cabecalho && !configuracao.disponivel()) {
            throw new IndisponivelException("Análise por IA indisponível: " + statusIa().motivo(), "IA_INDISPONIVEL");
        }
        analise.setStatus(cabecalho ? StatusAnaliseEdital.LENDO_EDITAL : StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        analise.setEtapaFalha(null);
        analise.setErro(null);
        repository.save(analise);
        if (cabecalho) agendar(id);
        return buscar(id);
    }

    /** Reiniciar o servidor no meio da leitura do cabeçalho deixa a análise em falha, com "tentar de novo". */
    @EventListener(ApplicationReadyEvent.class)
    public void marcarInterrompidas() {
        for (var analise : repository.findByStatusIn(Set.of(StatusAnaliseEdital.LENDO_EDITAL))) {
            falhar(analise, StatusAnaliseEdital.LENDO_EDITAL.name(), "Interrompida por reinício do servidor. Tente de novo.");
        }
    }

    private void agendar(Long id) {
        Runnable tarefa = () -> etapaCargos(id);
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
            falhar(analise, StatusAnaliseEdital.LENDO_EDITAL.name(), mensagem(e));
        }
    }

    // ---------------------------------------------------------------- resposta

    @Transactional(readOnly = true)
    public AnaliseEditalResponse resposta(AnaliseEdital analise) {
        return new AnaliseEditalResponse(
            analise.getId(), analise.getNomeArquivo(), analise.getStatus(), analise.getEtapaFalha(), analise.getErro(),
            analise.getModelo(), analise.getConcursoNome(), analise.getBanca(), analise.getDataProva(),
            cargosDe(analise), analise.getCargoEscolhido(), analise.getConcursoId(),
            analise.getCriadoEm(), analise.getAtualizadoEm());
    }

    // ---------------------------------------------------------------- o planejamento

    private static final java.util.Set<String> ETAPAS_DO_PLANEJAMENTO = java.util.Set.of("EXTRACAO", "ORGANIZACAO", "QUEBRA", "LIGACAO");

    /**
     * O planejamento não conseguiu ler o edital (docs/SPRINT-25-CONFERENCIA-EDITAL.md §4): a análise vira FALHOU com a
     * etapa e a mensagem, e "tentar de novo" a devolve a ele.
     */
    @Transactional
    public void falhaDoPlanejamento(Long id, String etapa, String disciplina, String mensagem) {
        var analise = buscar(id);
        exigir(analise, StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        if (etapa == null || !ETAPAS_DO_PLANEJAMENTO.contains(etapa)) {
            throw new ValidationException("Etapa desconhecida: " + etapa + ".", "ETAPA_DESCONHECIDA", "etapa");
        }
        var texto = cortar(mensagem, 2000);
        var completo = disciplina == null || disciplina.isBlank() ? texto : "[" + cortar(disciplina, 150) + "] " + texto;
        falhar(analise, etapa, completo == null ? "O planejamento não informou o motivo." : completo);
    }

    // ---------------------------------------------------------------- apoio

    private List<String> cargosDe(AnaliseEdital analise) {
        if (analise.getCargos() == null) return List.of();
        return List.of(jsonMapper.readValue(analise.getCargos(), String[].class));
    }

    void exigir(AnaliseEdital analise, StatusAnaliseEdital esperado) {
        if (analise.getStatus() != esperado) {
            throw new BusinessRuleException(
                "Esta ação não cabe agora: a análise está em " + analise.getStatus() + ".", "ETAPA_INVALIDA");
        }
    }

    void falhar(AnaliseEdital analise, String etapa, String mensagem) {
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

    private static LocalDate data(String texto) {
        if (texto == null) return null;
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static String cortar(String texto, int maximo) {
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
