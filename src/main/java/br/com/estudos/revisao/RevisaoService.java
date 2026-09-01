package br.com.estudos.revisao;

import java.util.List;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.sessao.Sessao;
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

    public RevisaoService(RevisaoRepository revisaoRepository, ParametroRepository parametroRepository) {
        this.revisaoRepository = revisaoRepository;
        this.parametroRepository = parametroRepository;
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
     * D-10 — mesma regra de consolidação usada no roteamento (§3.3),
     * exposta para o FrenteService não duplicá-la (docs/SPRINT-5-FRENTE.md §2,
     * ADR-033).
     */
    @Transactional(readOnly = true)
    public boolean estaConsolidado(Long assuntoId, TipoPeso peso) {
        return doisUltimosNoAlvoForamSucesso(assuntoId, nivelAlvo(peso));
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

        var revisao = new Revisao();
        revisao.setAssunto(assunto);
        revisao.setNivel(1);
        revisao.setDataPrevista(sessao.getData().plusDays(intervaloNivel(1)));
        revisao.setSessaoOrigem(sessao);
        revisao.setSituacao(SituacaoRevisao.PENDENTE);
        revisaoRepository.save(revisao);
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
        var jaConsolidado = nivelCumprido.equals(alvo) && doisUltimosNoAlvoForamSucesso(assunto.getId(), alvo);
        var consolidaAgora = !jaConsolidado
            && nivelCumprido.equals(alvo)
            && sessao.getResultado() == ResultadoSessao.SUCESSO
            && umUltimoNoAlvoFoiSucesso(assunto.getId(), alvo);

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

    private long intervaloDaPendente(Revisao pendente) {
        var alvo = nivelAlvo(pendente.getAssunto().getPeso());
        if (pendente.getNivel().equals(alvo) && doisUltimosNoAlvoForamSucesso(pendente.getAssunto().getId(), alvo)) {
            return intervaloManutencao();
        }
        return intervaloNivel(pendente.getNivel());
    }

    /**
     * "Escada concluída E os dois últimos resultados SUCESSO" (D-10,
     * `01_DOMINIO` §6.4) — as duas tentativas contadas têm que ser NO
     * nível-alvo, não em qualquer degrau anterior que tenha rendido SUCESSO
     * no caminho até ali (por isso o filtro por nível na consulta, não só
     * pelas duas últimas linhas cumpridas de qualquer nível).
     */
    private boolean doisUltimosNoAlvoForamSucesso(Long assuntoId, int alvo) {
        var ultimasDuas = revisaoRepository.findTop2ByAssuntoIdAndSituacaoAndNivelOrderByIdDesc(
            assuntoId, SituacaoRevisao.CUMPRIDA, alvo);
        return ultimasDuas.size() == 2
            && ultimasDuas.stream().allMatch(r -> r.getSessaoCumpriu().getResultado() == ResultadoSessao.SUCESSO);
    }

    private boolean umUltimoNoAlvoFoiSucesso(Long assuntoId, int alvo) {
        var ultimas = revisaoRepository.findTop2ByAssuntoIdAndSituacaoAndNivelOrderByIdDesc(
            assuntoId, SituacaoRevisao.CUMPRIDA, alvo);
        return !ultimas.isEmpty() && ultimas.get(0).getSessaoCumpriu().getResultado() == ResultadoSessao.SUCESSO;
    }

    /** D-23 (01_DOMINIO §6.7) — sem coluna própria, mapeamento fixo. */
    private int nivelAlvo(TipoPeso peso) {
        return switch (peso) {
            case ALTO -> 6;
            case MEDIO -> 4;
            case BAIXO -> 3;
        };
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
