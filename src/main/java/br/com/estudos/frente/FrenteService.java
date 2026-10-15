package br.com.estudos.frente;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.revisao.RevisaoRepository;
import br.com.estudos.revisao.EstadoConsolidacao;
import br.com.estudos.revisao.ResultadoCumpridoLinha;
import br.com.estudos.segmento.Segmento;
import br.com.estudos.segmento.SegmentoRepository;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.sessao.SessaoRepository;
import br.com.estudos.shared.enums.FaseAssunto;
import br.com.estudos.shared.enums.SituacaoRevisao;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.parametro.ParametroRepository;

/**
 * Frente, backlog e consolidado — tudo derivado de Assunto+Sessao+Revisao,
 * nada persistido (D-16, D-24, ADR-033). docs/SPRINT-5-FRENTE.md §2.
 */
@Service
public class FrenteService {

    private final AssuntoRepository assuntoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final SessaoRepository sessaoRepository;
    private final RevisaoRepository revisaoRepository;
    private final ParametroRepository parametroRepository;
    private final SegmentoRepository segmentoRepository;
    private final Clock clock;

    public FrenteService(
            AssuntoRepository assuntoRepository,
            DisciplinaRepository disciplinaRepository,
            SessaoRepository sessaoRepository,
            RevisaoRepository revisaoRepository,
            ParametroRepository parametroRepository,
            SegmentoRepository segmentoRepository,
            Clock clock) {
        this.assuntoRepository = assuntoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.sessaoRepository = sessaoRepository;
        this.revisaoRepository = revisaoRepository;
        this.parametroRepository = parametroRepository;
        this.segmentoRepository = segmentoRepository;
        this.clock = clock;
    }

    /** docs/SPRINT-5-FRENTE.md §2.1. */
    @Transactional(readOnly = true)
    public FaseAssunto fase(Long assuntoId) {
        var assunto = assuntoRepository.findById(assuntoId)
            .orElseThrow(() -> new NotFoundException("Assunto não encontrado.", "ASSUNTO_INEXISTENTE"));
        return fasesDe(List.of(assunto)).get(assunto.getId());
    }

    /** D-19 (contagens de FRENTE/BACKLOG/CONSOLIDADO, teto global) — docs/SPRINT-5-FRENTE.md §2.2. */
    @Transactional(readOnly = true)
    public FrenteResumoResponse resumo() {
        var assuntos = assuntoRepository.listarAtivosDeDisciplinasAtivas();
        var base = resumo(fasesDe(assuntos));
        // Balanço semanal (02_JORNADAS J-3): o que entrou e o que consolidou na
        // semana — só aqui, não no plano do turno, que não precisa disso.
        var hoje = LocalDate.now(clock);
        var inicioSemana = hoje.minusDays(6);
        return new FrenteResumoResponse(
            base.frenteAtual(), base.tetoGlobalFrente(), base.backlog(), base.consolidados(),
            base.represado(), base.tetoDiario(), base.alertaRepresamento(),
            base.estimativaDiasParaNormalizar(), base.avisoTetoInsuficiente(),
            entraramNaJanela(inicioSemana, hoje), consolidaramNaJanela(assuntos, inicioSemana, hoje));
    }

    private int entraramNaJanela(LocalDate inicio, LocalDate fim) {
        return (int) sessaoRepository.primeiraSessaoDosAtivos().stream()
            .filter(linha -> naJanela(linha.getPrimeira(), inicio, fim))
            .count();
    }

    private static boolean naJanela(LocalDate data, LocalDate inicio, LocalDate fim) {
        return !data.isBefore(inicio) && !data.isAfter(fim);
    }

    /** Consolidação vigente com data na janela — mesma regra de sequência (EstadoConsolidacao, ADR-033). */
    private int consolidaramNaJanela(List<Assunto> assuntos, LocalDate inicio, LocalDate fim) {
        if (assuntos.isEmpty()) {
            return 0;
        }
        var cumpridas = revisaoRepository.listarCumpridasDosAssuntos(assuntos.stream().map(Assunto::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(ResultadoCumpridoLinha::getAssuntoId));
        var total = 0;
        for (var assunto : assuntos) {
            var alvo = EstadoConsolidacao.nivelAlvo(assunto.getPeso());
            var noAlvo = cumpridas.getOrDefault(assunto.getId(), List.of()).stream()
                .filter(linha -> linha.getNivel() == alvo)
                .map(linha -> new EstadoConsolidacao.ResultadoNaData(linha.getResultado(), linha.getData()))
                .toList();
            var desde = EstadoConsolidacao.consolidadoDesde(noAlvo);
            if (desde.isPresent() && naJanela(desde.get(), inicio, fim)) {
                total++;
            }
        }
        return total;
    }

    /**
     * D-35 (01_DOMINIO §3.2.1): assuntos ativos cujos últimos N resultados são
     * todos PARCIAL. Lote abaixo do mínimo não conta (não chega a classificar).
     * Só sugere — nunca divide (docs/SPRINT-17-USO-REAL.md §6.1).
     */
    @Transactional(readOnly = true)
    public List<SugestaoDivisaoResponse> sugestoesDeDivisao() {
        var minimoParciais = valorParametroInt("parciais_seguidos_dividir");
        var loteMinimo = valorParametroInt("lote_minimo_questoes");
        var parciaisPorAssunto = new LinkedHashMap<Long, Integer>();
        var encerrados = new HashSet<Long>();
        for (var linha : sessaoRepository.listarResultadosDosAtivos()) { // mais recente primeiro, por assunto
            var assuntoId = linha.getAssuntoId();
            if (encerrados.contains(assuntoId)) continue;
            var lote = linha.getTipo() == TipoSessao.QUESTOES || linha.getTipo() == TipoSessao.FLASHCARDS;
            if (lote && linha.getQuestoesTotal() != null && linha.getQuestoesTotal() < loteMinimo) continue;
            if (linha.getResultado() == ResultadoSessao.PARCIAL) {
                parciaisPorAssunto.merge(assuntoId, 1, Integer::sum);
            } else {
                encerrados.add(assuntoId);
            }
        }
        var candidatos = parciaisPorAssunto.entrySet().stream()
            .filter(e -> e.getValue() >= minimoParciais)
            .toList();
        if (candidatos.isEmpty()) {
            return List.of();
        }
        var assuntos = assuntoRepository.findAllById(candidatos.stream().map(Map.Entry::getKey).toList()).stream()
            .collect(Collectors.toMap(Assunto::getId, a -> a));
        return candidatos.stream()
            .map(e -> {
                var assunto = assuntos.get(e.getKey());
                return new SugestaoDivisaoResponse(assunto.getId(), assunto.getNome(),
                    assunto.getDisciplina().getId(), assunto.getDisciplina().getNome(), e.getValue());
            })
            .sorted(Comparator.comparingInt(SugestaoDivisaoResponse::parciaisSeguidos).reversed()
                .thenComparing(SugestaoDivisaoResponse::assuntoId))
            .toList();
    }

    /** Represado, alerta e estimativa juntos, pra mensagem de retorno do turno (02_JORNADAS J-4). */
    @Transactional(readOnly = true)
    public SituacaoRepresamento situacaoRepresamento() {
        var represado = represado();
        var tetoDiario = valorParametroInt("teto_diario_recuperacoes");
        var alerta = alertaRepresamento(represado, tetoDiario);
        return new SituacaoRepresamento(represado, alerta, alerta ? represado / tetoDiario : 0);
    }

    public record SituacaoRepresamento(long represado, boolean alerta, long estimativaDiasParaNormalizar) {}

    /**
     * "Outro bloco de conteúdo" (02_JORNADAS §4.3): o segmento não lido seguinte
     * ao sugerido, no mesmo assunto, pela ordem (D-55).
     */
    @Transactional(readOnly = true)
    public Optional<Segmento> proximoSegmentoApos(Long assuntoId, int ordemSugerida) {
        var estudados = sessaoRepository.segmentosEstudados(assuntoId);
        return segmentoRepository.findByAssuntoIdOrderByOrdemAsc(assuntoId).stream()
            .filter(s -> s.getOrdem() > ordemSugerida && !estudados.contains(s.getId()))
            .findFirst();
    }

    // Contagens de fase e represamento — o que o plano do turno também usa;
    // os números da semana ficam no resumo público (J-3).
    private FrenteResumoResponse resumo(Map<Long, FaseAssunto> fases) {
        var frente = 0;
        var backlog = 0;
        var consolidados = 0;
        for (var fase : fases.values()) {
            switch (fase) {
                case FRENTE -> frente++;
                case BACKLOG -> backlog++;
                case CONSOLIDADO -> consolidados++;
            }
        }

        var represado = represado();
        var tetoDiario = valorParametroInt("teto_diario_recuperacoes");
        var alerta = alertaRepresamento(represado, tetoDiario);
        var estimativaDias = alerta ? represado / tetoDiario : 0;

        var tetoGlobalFrente = valorParametroInt("teto_global_frente");
        var avisoTeto = tetoInsuficienteParaAFrente(tetoDiario, tetoGlobalFrente);

        return new FrenteResumoResponse(
            frente, tetoGlobalFrente, backlog, consolidados,
            represado, tetoDiario, alerta, estimativaDias, avisoTeto, 0, 0);
    }

    /**
     * 01_DOMINIO §5.3, freio 2 — o atraso acumulado passou do limiar
     * (`represado > teto diário`, fechado na Sprint 5). Usado pelo resumo e,
     * via situacaoRepresamento, pelo plano do turno (D-56, J-4): uma regra só.
     */
    private static boolean alertaRepresamento(long represado, int tetoDiario) {
        return represado > tetoDiario;
    }

    private long represado() {
        return revisaoRepository.countBySituacaoAndDataPrevistaBefore(SituacaoRevisao.PENDENTE, LocalDate.now(clock));
    }

    /**
     * D-25 — vaga aberta por consolidação é preenchida do backlog da mesma
     * disciplina, respeitando o teto dela. Próximo assunto do backlog, por
     * ordem (D-41) — só se ainda houver vaga no teto por disciplina
     * (docs/SPRINT-5-FRENTE.md §2.4). Recomendação, nunca catraca
     * (01_DOMINIO §6.7 regra 6): quem decide o que fazer com a sugestão é o
     * chamador. D-21 (consolidar abre vaga automaticamente) é consequência
     * de {@link #fasesDe} não contar mais um assunto `CONSOLIDADO` como
     * `FRENTE` — nenhum código dedicado, a vaga já aparece na próxima
     * chamada.
     */
    @Transactional(readOnly = true)
    public Optional<Assunto> proximaVaga(Long disciplinaId) {
        var assuntosDaDisciplina = assuntoRepository.findByDisciplinaIdAndAtivoTrue(disciplinaId);
        return proximaVaga(assuntosDaDisciplina, fasesDe(assuntosDaDisciplina), valorParametroInt("teto_disciplina_frente"));
    }

    private Optional<Assunto> proximaVaga(List<Assunto> assuntosDaDisciplina, Map<Long, FaseAssunto> fases, int tetoDisciplina) {
        var frenteAtualDaDisciplina = assuntosDaDisciplina.stream()
            .filter(a -> fases.get(a.getId()) == FaseAssunto.FRENTE)
            .count();
        if (frenteAtualDaDisciplina >= tetoDisciplina) {
            return Optional.empty();
        }

        return assuntosDaDisciplina.stream()
            .filter(a -> fases.get(a.getId()) == FaseAssunto.BACKLOG)
            .min(Comparator.comparing(Assunto::getOrdem));
    }

    /**
     * D-19 — a frente é `{em estudo} ∪ {em escada}`, derivada, com teto
     * global e teto por disciplina (este método aplica o global; o de
     * disciplina vive em {@link #proximaVaga}). Frente já no teto global:
     * vazio, sem nem percorrer disciplina nenhuma (D-20 — frente cheia não
     * sugere assunto novo, mas não bloqueia abertura manual — não há endpoint
     * de bloqueio nenhum a impedir). D-56 — alerta de represamento ligado:
     * vazio também, o plano não contradiz o próprio alerta. D-57 — entre as
     * disciplinas ativas, a com menos assuntos na frente vem primeiro (empate
     * pelo id); a primeira que tiver vaga e backlog dá a sugestão
     * (docs/SPRINT-17-USO-REAL.md §3 e §4).
     */
    @Transactional(readOnly = true)
    public Optional<Assunto> proximaSugestaoDeConteudo() {
        // Fases calculadas uma vez para todos os assuntos ativos de disciplinas
        // ativas e reaproveitadas no resumo e em cada disciplina
        // (docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §3).
        var assuntos = assuntoRepository.listarAtivosDeDisciplinasAtivas();
        var fases = fasesDe(assuntos);
        var resumo = resumo(fases);
        if (resumo.frenteAtual() >= resumo.tetoGlobalFrente() || resumo.alertaRepresamento()) {
            return Optional.empty();
        }
        var porDisciplina = assuntos.stream()
            .collect(Collectors.groupingBy(a -> a.getDisciplina().getId()));
        var tetoDisciplina = valorParametroInt("teto_disciplina_frente");
        Comparator<Long> menosNaFrente = Comparator.comparingLong(disciplinaId ->
            porDisciplina.getOrDefault(disciplinaId, List.of()).stream()
                .filter(a -> fases.get(a.getId()) == FaseAssunto.FRENTE)
                .count());
        var disciplinas = disciplinaRepository.findByAtivoTrue().stream()
            .map(d -> d.getId())
            .sorted(menosNaFrente.thenComparing(Comparator.naturalOrder()))
            .toList();
        for (var disciplinaId : disciplinas) {
            var sugestao = proximaVaga(porDisciplina.getOrDefault(disciplinaId, List.of()), fases, tetoDisciplina);
            if (sugestao.isPresent()) return sugestao;
        }
        return Optional.empty();
    }

    /**
     * O bloco de conteúdo do turno: continuar a leitura de um assunto em estudo
     * (D-55) antes de abrir um do backlog (D-20/D-25). Continuar não abre vaga
     * nova, então não passa pelo teto da frente. docs/SPRINT-17-USO-REAL.md §1.2.
     */
    @Transactional(readOnly = true)
    public Optional<SugestaoConteudo> sugestaoDeConteudo() {
        return proximaContinuacao().or(() -> proximaSugestaoDeConteudo().map(assunto -> {
            var segmentos = segmentoRepository.findByAssuntoIdOrderByOrdemAsc(assunto.getId());
            return new SugestaoConteudo(assunto, segmentos.isEmpty() ? null : segmentos.get(0), segmentos.size(), false);
        }));
    }

    /**
     * D-55 — o assunto em estudo há mais tempo (01_DOMINIO §8: com sessão, sem
     * revisão) que ainda tem segmento sem sessão ESTUDO; sugere o de menor ordem.
     */
    @Transactional(readOnly = true)
    public Optional<SugestaoConteudo> proximaContinuacao() {
        for (var assuntoId : sessaoRepository.assuntosEmEstudo()) {
            var segmentos = segmentoRepository.findByAssuntoIdOrderByOrdemAsc(assuntoId);
            var estudados = sessaoRepository.segmentosEstudados(assuntoId);
            var proximo = segmentos.stream().filter(s -> !estudados.contains(s.getId())).findFirst();
            if (proximo.isPresent()) {
                var assunto = assuntoRepository.findById(assuntoId).orElseThrow();
                return Optional.of(new SugestaoConteudo(assunto, proximo.get(), segmentos.size(), true));
            }
        }
        return Optional.empty();
    }

    /**
     * D-16/D-24 em lote — duas consultas para o conjunto inteiro, o resto em
     * memória (docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §3). Consolidado sai de
     * EstadoConsolidacao, a mesma função do roteamento (ADR-033).
     */
    @Transactional(readOnly = true)
    public Map<Long, FaseAssunto> fasesDe(List<Assunto> assuntos) {
        var fases = new HashMap<Long, FaseAssunto>();
        if (assuntos.isEmpty()) {
            return fases;
        }
        var ids = assuntos.stream().map(Assunto::getId).toList();
        var comSessao = sessaoRepository.assuntosComSessao(ids);
        var cumpridas = revisaoRepository.listarCumpridasDosAssuntos(ids).stream()
            .collect(Collectors.groupingBy(ResultadoCumpridoLinha::getAssuntoId));

        for (var assunto : assuntos) {
            if (!assunto.getDisciplina().isAtivo() || !comSessao.contains(assunto.getId())) {
                fases.put(assunto.getId(), FaseAssunto.BACKLOG); // D-24
                continue;
            }
            var alvo = EstadoConsolidacao.nivelAlvo(assunto.getPeso());
            var resultadosNoAlvo = cumpridas.getOrDefault(assunto.getId(), List.of()).stream()
                .filter(linha -> linha.getNivel() == alvo)
                .map(ResultadoCumpridoLinha::getResultado)
                .toList();
            fases.put(assunto.getId(), EstadoConsolidacao.consolidado(resultadosNoAlvo)
                ? FaseAssunto.CONSOLIDADO
                : FaseAssunto.FRENTE);
        }
        return fases;
    }

    /**
     * D-09... D-31 (01_DOMINIO §7.5): piso do melhor caso — mesmo com a
     * frente inteira consolidada (nenhum assunto subindo escada, todos em
     * manutenção, a carga mínima possível), sustentar `tetoGlobalFrente`
     * assuntos em manutenção exige `tetoGlobalFrente` revisões a cada
     * `intervalo_manutencao_dias` dias. Capacidade nesse período é
     * `tetoDiario × intervalo`; abaixo de `tetoGlobalFrente`, represamento é
     * matematicamente garantido, não estimativa — comparação por
     * multiplicação, não divisão, pra não arredondar a garantia (docs/
     * SPRINT-5-FRENTE.md §2.4). Não modela a fase de subida da escada
     * (assuntos novos pesam mais que os em manutenção) — é piso, não média.
     */
    private boolean tetoInsuficienteParaAFrente(int tetoDiario, int tetoGlobalFrente) {
        var intervaloManutencao = valorParametroInt("intervalo_manutencao_dias");
        return (long) tetoDiario * intervaloManutencao < tetoGlobalFrente;
    }

    private int valorParametroInt(String chave) {
        return parametroRepository.findById(chave)
            .map(p -> Integer.parseInt(p.getValor()))
            .orElseThrow(() -> new IllegalStateException("Parâmetro obrigatório ausente: " + chave));
    }
}
