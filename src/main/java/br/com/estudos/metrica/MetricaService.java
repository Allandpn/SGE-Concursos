package br.com.estudos.metrica;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.revisao.RevisaoRepository;
import br.com.estudos.sessao.Sessao;
import br.com.estudos.sessao.SessaoRepository;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.simulado.ResultadoSimuladoRepository;

/**
 * M-1 a M-4, consultas puras — nenhum endpoint grava nada (ADR-033: consulta
 * Spring Data + agregação em Java, reaproveitada, sem view nem cache).
 * docs/SPRINT-7-METRICAS.md §4.
 */
@Service
public class MetricaService {

    // 00_PRODUTO §7: abaixo de 10, "—" nunca "0%"; a partir de 100, a tela
    // pode mostrar seta/cor/comparação. Regra de apresentação fixa, não
    // parametro (docs/SPRINT-7-METRICAS.md §2).
    private static final int N_MINIMO_EXIBICAO = 10;
    private static final int N_CONFIAVEL = 100;

    // M-4 (00_PRODUTO §7 M-4): prazo fixo, distinto da janela de tolerância
    // percentual de D-38.
    private static final int DIAS_TOLERANCIA_ADERENCIA = 3;

    private final SessaoRepository sessaoRepository;
    private final RevisaoRepository revisaoRepository;
    private final ResultadoSimuladoRepository resultadoSimuladoRepository;
    private final Clock clock;

    public MetricaService(
            SessaoRepository sessaoRepository,
            RevisaoRepository revisaoRepository,
            ResultadoSimuladoRepository resultadoSimuladoRepository,
            Clock clock) {
        this.sessaoRepository = sessaoRepository;
        this.revisaoRepository = revisaoRepository;
        this.resultadoSimuladoRepository = resultadoSimuladoRepository;
        this.clock = clock;
    }

    /**
     * docs/SPRINT-7-METRICAS.md §4.1, estendida por docs/SPRINT-8-SIMULADO.md
     * §4: simulado soma no mesmo agregado de QUESTOES do mesmo
     * disciplina+formato — "a entrada mais pura que M-1 pode ter", não uma
     * quarta série ao lado dela.
     */
    @Transactional(readOnly = true)
    public M1Response m1(JanelaMetrica janela) {
        var hoje = LocalDate.now(clock);
        var inicio = janela == JanelaMetrica.GLOBAL ? hoje.minusMonths(3) : hoje.minusYears(1);
        var linhas = new ArrayList<AcertoLinha>(sessaoRepository.listarAcertoQuestoes(inicio, hoje));
        linhas.addAll(resultadoSimuladoRepository.listarAcerto(inicio, hoje));

        var agrupado = new LinkedHashMap<ChaveM1, int[]>();
        for (var linha : linhas) {
            var disciplinaId = janela == JanelaMetrica.GLOBAL ? null : linha.getDisciplinaId();
            var chave = new ChaveM1(disciplinaId, linha.getFormato());
            var soma = agrupado.computeIfAbsent(chave, k -> new int[2]);
            soma[0] += linha.getQuestoesCorretas();
            soma[1] += linha.getQuestoesTotal();
        }

        var resposta = agrupado.entrySet().stream()
            .map(e -> {
                var acertos = e.getValue()[0];
                var total = e.getValue()[1];
                var percentual = total < N_MINIMO_EXIBICAO ? null : (Double) (acertos * 100.0 / total);
                return new M1LinhaResponse(e.getKey().disciplinaId(), e.getKey().formato(), acertos, total, total >= N_CONFIAVEL, percentual);
            })
            .toList();

        return new M1Response(janela, resposta);
    }

    private record ChaveM1(Long disciplinaId, FormatoBanca formato) {}

    /** docs/SPRINT-7-METRICAS.md §4.2. */
    @Transactional(readOnly = true)
    public M2Response m2(Long assuntoId) {
        var todas = sessaoRepository.findByAssuntoIdAndTipoInOrderByDataAsc(
            assuntoId, List.of(TipoSessao.QUESTOES, TipoSessao.FLASHCARDS, TipoSessao.RECUPERACAO));

        var porTipo = new LinkedHashMap<TipoSessao, List<Sessao>>();
        for (var sessao : todas) {
            porTipo.computeIfAbsent(sessao.getTipo(), t -> new ArrayList<>()).add(sessao);
        }

        var series = new ArrayList<M2SerieResponse>();
        for (var tipo : List.of(TipoSessao.QUESTOES, TipoSessao.FLASHCARDS)) {
            var lista = porTipo.get(tipo);
            if (lista != null && !lista.isEmpty()) series.add(serieDePercentualDeQuestoes(tipo, lista));
        }
        var recuperacao = porTipo.get(TipoSessao.RECUPERACAO);
        if (recuperacao != null && !recuperacao.isEmpty()) series.add(serieDeRecuperacao(recuperacao));

        return new M2Response(assuntoId, series);
    }

    private M2SerieResponse serieDePercentualDeQuestoes(TipoSessao tipo, List<Sessao> sessoes) {
        var primeira = percentualDeQuestoes(sessoes.get(0));
        var media = mediaDe(sessoes.stream().skip(1).map(this::percentualDeQuestoes).toList());
        return new M2SerieResponse(tipo, primeira, media, sessoes.size());
    }

    private M2SerieResponse serieDeRecuperacao(List<Sessao> sessoes) {
        var primeira = percentualDeRecuperacao(sessoes.get(0));
        var media = mediaDe(sessoes.stream().skip(1).map(this::percentualDeRecuperacao).toList());
        return new M2SerieResponse(TipoSessao.RECUPERACAO, primeira, media, sessoes.size());
    }

    // ck_sessao_questoes_por_tipo (V1) já garante corretas/total presentes e
    // > 0 pra QUESTOES/FLASHCARDS — sem checagem de nulo aqui.
    private double percentualDeQuestoes(Sessao sessao) {
        return sessao.getQuestoesCorretas() * 100.0 / sessao.getQuestoesTotal();
    }

    private double percentualDeRecuperacao(Sessao sessao) {
        return sessao.getResultado() != ResultadoSessao.FALHA ? 100.0 : 0.0;
    }

    private Double mediaDe(List<Double> valores) {
        return valores.isEmpty() ? null : valores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    /** docs/SPRINT-7-METRICAS.md §4.3. */
    @Transactional(readOnly = true)
    public M3Response m3() {
        var objetivas = sessaoRepository.findByTipoInAndPrevisaoPercentualNotNull(
            List.of(TipoSessao.QUESTOES, TipoSessao.FLASHCARDS));
        var desvios = objetivas.stream()
            .mapToDouble(s -> s.getPrevisaoPercentual() - percentualDeQuestoes(s))
            .toArray();
        var mediaDesvio = desvios.length == 0 ? 0.0 : Arrays.stream(desvios).average().orElseThrow();
        var objetiva = new M3ObjetivaResponse(mediaDesvio, desvios.length);

        var subjetivas = sessaoRepository.findByTipoAndPrevisaoReconstrucaoNotNull(TipoSessao.RECUPERACAO);
        var superestimou = 0;
        var subestimou = 0;
        var acertouPrevisao = 0;
        for (var sessao : subjetivas) {
            var previu = sessao.getPrevisaoReconstrucao();
            var conseguiu = sessao.getResultado() != ResultadoSessao.FALHA;
            if (previu && !conseguiu) superestimou++;
            else if (!previu && conseguiu) subestimou++;
            else acertouPrevisao++;
        }
        var subjetiva = new M3SubjetivaResponse(superestimou, subestimou, acertouPrevisao, subjetivas.size());

        return new M3Response(objetiva, subjetiva);
    }

    /** docs/SPRINT-7-METRICAS.md §4.4 — percentual sobre as revisões concluídas, nunca sobre o total agendado. */
    @Transactional(readOnly = true)
    public M4Response m4() {
        var cumpridas = revisaoRepository.listarCumpridasComSessao();
        var dentroDoPrazo = (int) cumpridas.stream()
            .filter(r -> Math.abs(ChronoUnit.DAYS.between(r.getDataPrevista(), r.getSessaoCumpriu().getData())) <= DIAS_TOLERANCIA_ADERENCIA)
            .count();
        var total = cumpridas.size();
        Double percentual = total == 0 ? null : dentroDoPrazo * 100.0 / total;

        return new M4Response(percentual, dentroDoPrazo, total);
    }
}
