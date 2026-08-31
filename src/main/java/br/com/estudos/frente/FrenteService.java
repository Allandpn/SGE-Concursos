package br.com.estudos.frente;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.revisao.RevisaoRepository;
import br.com.estudos.revisao.RevisaoService;
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
    private final RevisaoService revisaoService;
    private final ParametroRepository parametroRepository;
    private final Clock clock;

    public FrenteService(
            AssuntoRepository assuntoRepository,
            DisciplinaRepository disciplinaRepository,
            SessaoRepository sessaoRepository,
            RevisaoRepository revisaoRepository,
            RevisaoService revisaoService,
            ParametroRepository parametroRepository,
            Clock clock) {
        this.assuntoRepository = assuntoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.sessaoRepository = sessaoRepository;
        this.revisaoRepository = revisaoRepository;
        this.revisaoService = revisaoService;
        this.parametroRepository = parametroRepository;
        this.clock = clock;
    }

    /** docs/SPRINT-5-FRENTE.md §2.1. */
    @Transactional(readOnly = true)
    public FaseAssunto fase(Long assuntoId) {
        var assunto = assuntoRepository.findById(assuntoId)
            .orElseThrow(() -> new NotFoundException("Assunto não encontrado.", "ASSUNTO_INEXISTENTE"));
        return faseDe(assunto);
    }

    /** docs/SPRINT-5-FRENTE.md §2.2. */
    @Transactional(readOnly = true)
    public FrenteResumoResponse resumo() {
        var assuntos = assuntoRepository.listarAtivosDeDisciplinasAtivas();

        var frente = 0;
        var backlog = 0;
        var consolidados = 0;
        for (var assunto : assuntos) {
            switch (faseDe(assunto)) {
                case FRENTE -> frente++;
                case BACKLOG -> backlog++;
                case CONSOLIDADO -> consolidados++;
            }
        }

        var hoje = LocalDate.now(clock);
        var represado = revisaoRepository.countBySituacaoAndDataPrevistaBefore(SituacaoRevisao.PENDENTE, hoje);
        var tetoDiario = valorParametroInt("teto_diario_recuperacoes");
        var alerta = represado > tetoDiario;
        var estimativaDias = alerta ? represado / tetoDiario : 0;

        return new FrenteResumoResponse(
            frente, valorParametroInt("teto_global_frente"), backlog, consolidados,
            represado, tetoDiario, alerta, estimativaDias);
    }

    /**
     * Próximo assunto do backlog de uma disciplina, por ordem (D-41) — só se
     * ainda houver vaga no teto por disciplina (docs/SPRINT-5-FRENTE.md §2.3).
     * Recomendação, nunca catraca (01_DOMINIO §6.7 regra 6): quem decide o
     * que fazer com a sugestão é o chamador.
     */
    @Transactional(readOnly = true)
    public Optional<Assunto> proximaVaga(Long disciplinaId) {
        var assuntosDaDisciplina = assuntoRepository.findByDisciplinaIdAndAtivoTrue(disciplinaId);

        var frenteAtualDaDisciplina = assuntosDaDisciplina.stream()
            .filter(a -> faseDe(a) == FaseAssunto.FRENTE)
            .count();
        if (frenteAtualDaDisciplina >= valorParametroInt("teto_disciplina_frente")) {
            return Optional.empty();
        }

        return assuntosDaDisciplina.stream()
            .filter(a -> faseDe(a) == FaseAssunto.BACKLOG)
            .min(Comparator.comparing(Assunto::getOrdem));
    }

    /**
     * Primeira disciplina ativa, em ordem estável, que ainda tem vaga e
     * backlog — reaproveita {@link #proximaVaga} (docs/SPRINT-6-TURNO.md §1).
     * Frente já no teto global: vazio, sem nem percorrer disciplina nenhuma
     * (01_DOMINIO §6.7 regra 4 — frente cheia não sugere assunto novo).
     */
    @Transactional(readOnly = true)
    public Optional<Assunto> proximaSugestaoDeConteudo() {
        var resumo = resumo();
        if (resumo.frenteAtual() >= resumo.tetoGlobalFrente()) {
            return Optional.empty();
        }
        for (var disciplina : disciplinaRepository.findByAtivoTrue()) {
            var sugestao = proximaVaga(disciplina.getId());
            if (sugestao.isPresent()) return sugestao;
        }
        return Optional.empty();
    }

    private FaseAssunto faseDe(Assunto assunto) {
        if (!assunto.getDisciplina().isAtivo()) {
            return FaseAssunto.BACKLOG; // D-24
        }
        if (!sessaoRepository.existsByAssuntoId(assunto.getId())) {
            return FaseAssunto.BACKLOG; // D-24
        }
        return revisaoService.estaConsolidado(assunto.getId(), assunto.getPeso())
            ? FaseAssunto.CONSOLIDADO
            : FaseAssunto.FRENTE;
    }

    private int valorParametroInt(String chave) {
        return parametroRepository.findById(chave)
            .map(p -> Integer.parseInt(p.getValor()))
            .orElseThrow(() -> new IllegalStateException("Parâmetro obrigatório ausente: " + chave));
    }
}
