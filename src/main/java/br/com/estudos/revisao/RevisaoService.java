package br.com.estudos.revisao;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.segmento.SegmentoRepository;
import br.com.estudos.sessao.Sessao;
import br.com.estudos.sessao.SessaoRepository;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.SituacaoRevisao;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.parametro.ParametroRepository;

/**
 * Agendamento, cumprimento e roteamento da escada (docs/SPRINT-4-ESCADA.md
 * §3). Chamado por {@link br.com.estudos.sessao.SessaoService#gravar}, mesma
 * transação — nenhum endpoint cria/edita Revisao direto.
 */
@Service
public class RevisaoService {

    private final RevisaoRepository revisaoRepository;
    private final ParametroRepository parametroRepository;
    private final SegmentoRepository segmentoRepository;
    private final SessaoRepository sessaoRepository;
    private final Clock clock;

    public RevisaoService(
            RevisaoRepository revisaoRepository,
            ParametroRepository parametroRepository,
            SegmentoRepository segmentoRepository,
            SessaoRepository sessaoRepository,
            Clock clock) {
        this.revisaoRepository = revisaoRepository;
        this.parametroRepository = parametroRepository;
        this.segmentoRepository = segmentoRepository;
        this.sessaoRepository = sessaoRepository;
        this.clock = clock;
    }

    /** docs/SPRINT-4-ESCADA.md §3.1/§3.2. */
    public void processarSessao(Sessao sessao) {
        switch (sessao.getTipo()) {
            case ESTUDO -> agendarPrimeira(sessao);
            case QUESTOES, FLASHCARDS, RECUPERACAO -> processarRecuperacao(sessao);
        }
    }

    /** Busca uma revisão pelo id. Lança se não existir. */
    @Transactional(readOnly = true)
    public Revisao buscar(Long id) {
        return revisaoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Revisão não encontrada.", "REVISAO_INEXISTENTE"));
    }

    /** Lista as revisões de um assunto. */
    @Transactional(readOnly = true)
    public List<Revisao> listar(Long assuntoId) {
        return revisaoRepository.findByAssuntoId(assuntoId);
    }

    /** D-17 (docs/SPRINT-4-ESCADA.md §3.4) — chamado por AssuntoService.arquivar. */
    public void cancelarPendentePorAssunto(Long assuntoId) {
        revisaoRepository.cancelarPendentePorAssunto(assuntoId);
    }

    /** D-17 — chamado por DisciplinaService.arquivar. */
    public void cancelarPendentesPorDisciplina(Long disciplinaId) {
        revisaoRepository.cancelarPendentesPorDisciplina(disciplinaId);
    }

    /**
     * D-49 (01_DOMINIO §3.4) — chamado por AssuntoService.reativar e, em
     * laço, por DisciplinaService.reativar (um assunto por vez, mesmo
     * método: reativar disciplina não é um caminho especial). Nunca reescreve
     * a CANCELADA (D-18); se ela existir, nasce uma PENDENTE nova no mesmo
     * nível — reusa intervaloDaPendente para herdar o intervalo de manutenção
     * se o assunto já estava consolidado, não só o intervalo de escada.
     */
    public void restaurarPendenteSeCancelada(Long assuntoId) {
        var ultima = revisaoRepository.buscarUltimaPorAssunto(assuntoId);
        if (ultima.isEmpty() || ultima.get().getSituacao() != SituacaoRevisao.CANCELADA) {
            return;
        }
        var cancelada = ultima.get();

        var nova = new Revisao();
        nova.setAssunto(cancelada.getAssunto());
        nova.setNivel(cancelada.getNivel());
        nova.setDataPrevista(LocalDate.now(clock).plusDays(intervaloDaPendente(cancelada)));
        nova.setSessaoOrigem(cancelada.getSessaoOrigem());
        nova.setSituacao(SituacaoRevisao.PENDENTE);
        revisaoRepository.save(nova);
    }

    /**
     * Checagem prévia de propósito — foge do padrão D-05/D-45 do resto do
     * sistema (nunca checar antes, deixar o banco recusar e traduzir). Aqui
     * uma falha de flush no INSERT deixaria a sessão do Hibernate inutilizável
     * pro resto desta transação, que é a MESMA transação salvando a
     * {@link Sessao} — e ao contrário do caso de D-45 (Sprint 3), não dá pra
     * isolar numa transação nova: a `Revisao` referencia a `Sessao` que ainda
     * não commitou, então precisam estar na mesma transação. A restrição
     * `ux_revisao_d05_pendente_por_assunto` continua existindo como rede de
     * segurança contra corrida real (rara, sistema de um usuário só).
     */
    private void agendarPrimeira(Sessao sessao) {
        var assunto = sessao.getAssunto();
        var jaTemPendente = revisaoRepository.findByAssuntoIdAndSituacao(assunto.getId(), SituacaoRevisao.PENDENTE).isPresent();
        if (jaTemPendente) {
            return; // estudar de novo com revisão já agendada é uso legítimo (§3.1) — só não duplica.
        }
        // D-54: num assunto com segmentos, a escada só começa quando a leitura
        // termina. ESTUDO sem segmento é "o assunto inteiro" e começa já
        // (docs/SPRINT-17-USO-REAL.md §1.1).
        if (sessao.getSegmento() != null && !leituraCompleta(assunto.getId())) {
            return;
        }

        var revisao = new Revisao();
        revisao.setAssunto(assunto);
        revisao.setNivel(1);
        revisao.setDataPrevista(sessao.getData().plusDays(intervaloNivel(1)));
        revisao.setSessaoOrigem(sessao);
        revisao.setSituacao(SituacaoRevisao.PENDENTE);
        revisaoRepository.save(revisao);
    }

    /**
     * 01_DOMINIO §5.4 / 02_JORNADAS §4.3 — pendentes ainda não vencidas cuja
     * janela de tolerância já abriu hoje: antecipá-las cumpre a revisão, mas
     * encurta o espaçamento (a tela diz isso junto da opção). Fora da janela
     * não entra: estudar continua livre, mas a revisão não seria cumprida.
     */
    @Transactional(readOnly = true)
    public List<Revisao> antecipaveis(LocalDate hoje, int limite) {
        return revisaoRepository.listarFuturas(hoje).stream()
            .filter(pendente -> dentroDaJanela(pendente, hoje))
            .limit(limite)
            .toList();
    }

    /** D-54 — todo segmento do assunto tem alguma sessão ESTUDO (a desta transação inclusive). */
    private boolean leituraCompleta(Long assuntoId) {
        var estudados = sessaoRepository.segmentosEstudados(assuntoId);
        return segmentoRepository.findByAssuntoIdAndArquivadoEmIsNullOrderByOrdemAsc(assuntoId).stream()
            .allMatch(segmento -> estudados.contains(segmento.getId()));
    }

    private void processarRecuperacao(Sessao sessao) {
        if (loteAbaixoDoMinimo(sessao)) {
            return; // D-09 (01_DOMINIO §4.3): esforço vale, crédito não — mesma família de D-39 (§5.4).
        }

        var assunto = sessao.getAssunto();
        var pendenteOpt = revisaoRepository.findByAssuntoIdAndSituacao(assunto.getId(), SituacaoRevisao.PENDENTE);

        if (pendenteOpt.isEmpty()) {
            // Recuperação espontânea (01_DOMINIO §3.3): roteia a partir do nível 1
            // implícito, sem nada para marcar CUMPRIDA.
            criarProxima(sessao, 1, nivelAlvo(assunto.getPeso()), false);
            return;
        }

        var pendente = pendenteOpt.get();
        if (!dentroDaJanela(pendente, sessao.getData())) {
            return; // §3.2: fora da janela, a sessão vale, a revisão pendente não muda.
        }

        var alvo = nivelAlvo(assunto.getPeso());
        var nivelCumprido = pendente.getNivel();
        // 01_DOMINIO §5.5 (v1.19.0): o estado antes desta sessão decide a linha
        // da tabela de roteamento; consolidar agora só acontece com SUCESSO no
        // alvo completando dois seguidos (docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §1.1).
        var jaConsolidado = false;
        var consolidaAgora = false;
        if (nivelCumprido.equals(alvo)) {
            var anteriores = resultadosNoAlvo(assunto.getId(), alvo);
            jaConsolidado = EstadoConsolidacao.consolidado(anteriores);
            if (!jaConsolidado) {
                var comEsta = new ArrayList<>(anteriores);
                comEsta.add(sessao.getResultado());
                consolidaAgora = EstadoConsolidacao.consolidado(comEsta);
            }
        }

        pendente.setSituacao(SituacaoRevisao.CUMPRIDA);
        pendente.setSessaoCumpriu(sessao);
        try {
            // saveAndFlush, não save: entidade gerenciada (veio de findBy...),
            // save() sozinho não força escrita — o INSERT da próxima pendente
            // logo abaixo rodaria antes deste UPDATE ir pro banco e colidiria
            // com a própria linha que este UPDATE estava prestes a liberar
            // (mesmo defeito do item 2.3, AssuntoService.atualizar).
            revisaoRepository.saveAndFlush(pendente);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictException(
                "Outra atualização concorrente na mesma revisão pendente.", "REVISAO_CONCORRENTE");
        }

        criarProxima(sessao, nivelCumprido, alvo, jaConsolidado || consolidaAgora);
    }

    /** docs/SPRINT-4-ESCADA.md §3.3 — D-07 (não consolidado) / D-10 e D-11 (consolidado). */
    private void criarProxima(Sessao sessao, int nivelCumprido, int alvo, boolean consolidado) {
        var resultado = sessao.getResultado();
        int proximoNivel;
        long proximoIntervaloDias;

        if (consolidado) {
            proximoNivel = alvo;
            proximoIntervaloDias = switch (resultado) {
                case SUCESSO -> intervaloManutencao();
                case PARCIAL -> intervaloManutencao() / 2; // "antecipa a próxima para metade" (01_DOMINIO §5.5)
                case FALHA -> intervaloNivel(alvo); // D-11: sai de consolidado, volta à escada no nível-alvo
            };
        } else {
            proximoNivel = switch (resultado) {
                case SUCESSO -> Math.min(nivelCumprido + 1, alvo);
                case PARCIAL -> nivelCumprido;
                case FALHA -> Math.max(nivelCumprido - 1, 1);
            };
            proximoIntervaloDias = intervaloNivel(proximoNivel);
        }

        var proxima = new Revisao();
        proxima.setAssunto(sessao.getAssunto());
        proxima.setNivel(proximoNivel);
        proxima.setDataPrevista(sessao.getData().plusDays(proximoIntervaloDias));
        proxima.setSessaoOrigem(sessao);
        proxima.setSituacao(SituacaoRevisao.PENDENTE);
        revisaoRepository.save(proxima);
    }

    /**
     * D-09 (01_DOMINIO §4.3) — só QUESTOES/FLASHCARDS têm "lote"; RECUPERACAO
     * é uma tentativa declarada, não uma contagem que possa ser pequena
     * demais para classificar.
     */
    private boolean loteAbaixoDoMinimo(Sessao sessao) {
        if (sessao.getTipo() != TipoSessao.QUESTOES && sessao.getTipo() != TipoSessao.FLASHCARDS) {
            return false;
        }
        return sessao.getQuestoesTotal() < valorParametroLong("lote_minimo_questoes");
    }

    /** docs/SPRINT-4-ESCADA.md §3.2 — janela de tolerância (01_DOMINIO §5.4). */
    private boolean dentroDaJanela(Revisao pendente, java.time.LocalDate dataSessao) {
        var intervaloDoNivel = intervaloDaPendente(pendente);
        var toleranciaPercentual = valorParametroLong("janela_tolerancia_percentual");
        var toleranciaDias = intervaloDoNivel * toleranciaPercentual / 100;
        var inicioJanela = pendente.getDataPrevista().minusDays(toleranciaDias);
        return !dataSessao.isBefore(inicioJanela);
    }

    /**
     * O intervalo que foi de fato agendado para esta revisão — não
     * reconstruído do estado atual, que errava depois de um PARCIAL em
     * manutenção (agendado na metade). docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §1.2.
     */
    private long intervaloDaPendente(Revisao pendente) {
        var origem = pendente.getSessaoOrigem();
        if (origem == null) {
            return intervaloNivel(pendente.getNivel()); // coluna admite nulo; o código nunca deixa
        }
        return ChronoUnit.DAYS.between(origem.getData(), pendente.getDataPrevista());
    }

    private List<ResultadoSessao> resultadosNoAlvo(Long assuntoId, int alvo) {
        return revisaoRepository.listarResultadosCumpridos(assuntoId, alvo);
    }

    private int nivelAlvo(TipoPeso peso) {
        return EstadoConsolidacao.nivelAlvo(peso);
    }

    /** D-08 — o intervalo de um nível é sempre o mesmo parâmetro, nunca recalculado a partir do histórico. */
    private long intervaloNivel(int nivel) {
        return valorParametroLong("intervalo_nivel_" + nivel);
    }

    private long intervaloManutencao() {
        return valorParametroLong("intervalo_manutencao_dias");
    }

    private long valorParametroLong(String chave) {
        return parametroRepository.findById(chave)
            .map(p -> Long.parseLong(p.getValor()))
            .orElseThrow(() -> new IllegalStateException("Parâmetro obrigatório ausente: " + chave));
    }
}
