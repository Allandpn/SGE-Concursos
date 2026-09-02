package br.com.estudos.metrica;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.sessao.SessaoRequest;
import br.com.estudos.sessao.SessaoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.simulado.ResultadoSimuladoRequest;
import br.com.estudos.simulado.SimuladoRequest;
import br.com.estudos.simulado.SimuladoService;

/**
 * M-1 a M-4 (docs/SPRINT-7-METRICAS.md §6). Item 7.7 de PROGRESSO.md.
 *
 * M-1 usa um "hoje" fixado bem no futuro ({@link RelogioFixoConfig}): a
 * janela (trimestral/anual) então nunca alcança dados de outras classes de
 * teste não-transacionais que ficam de verdade no banco compartilhado
 * (mesmo problema de poluição entre classes já documentado no changelog
 * 1.14.0 de PROGRESSO.md). M-3/M-4 são globais, sem janela — não dá pra
 * isolar por data, então os testes deles comparam o "antes" e o "depois" em
 * vez de valor absoluto, robusto a qualquer poluição pré-existente.
 */
@Transactional
class MetricaServiceTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.of(2999, 1, 1);

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Autowired
    private SessaoService sessaoService;

    @Autowired
    private MetricaService metricaService;

    @Autowired
    private SimuladoService simuladoService;

    private Disciplina novaDisciplina() {
        return disciplinaService.criar(new DisciplinaRequest("Disciplina Metrica " + UUID.randomUUID(), TipoPeso.MEDIO));
    }

    private Assunto novoAssunto(Long disciplinaId) {
        return novoAssunto(disciplinaId, 1);
    }

    // D-48: ordem única por disciplina entre ativos — testes com mais de um
    // assunto na mesma disciplina precisam de ordem distinta.
    private Assunto novoAssunto(Long disciplinaId, int ordem) {
        return assuntoService.criar(new AssuntoRequest(disciplinaId, "Assunto Metrica " + UUID.randomUUID(), TipoPeso.MEDIO, (short) 3, ordem));
    }

    private void registrarQuestoes(Long assuntoId, LocalDate data, int corretas, int total, FormatoBanca formato, Short previsao) {
        sessaoService.registrar(new SessaoRequest(
            assuntoId, TipoSessao.QUESTOES, data, 20, corretas, total, formato, previsao, null, null,
            UUID.randomUUID()));
    }

    private void registrarRecuperacao(Long assuntoId, LocalDate data, ResultadoSessao previsaoReconstrucao, ResultadoSessao resultado) {
        sessaoService.registrar(new SessaoRequest(
            assuntoId, TipoSessao.RECUPERACAO, data, 8, null, null, null, null, previsaoReconstrucao, resultado,
            UUID.randomUUID()));
    }

    @Test
    void m1_naoSomaFormatosENMinimoEConfiavelBatemComOTexto() {
        var disciplina = novaDisciplina();
        var assunto = novoAssunto(disciplina.getId());
        registrarQuestoes(assunto.getId(), HOJE, 90, 150, FormatoBanca.MULTIPLA_ESCOLHA, (short) 50); // n=150 -> confiavel, 60%
        registrarQuestoes(assunto.getId(), HOJE, 3, 5, FormatoBanca.CERTO_ERRADO, (short) 50); // n=5 -> "-", nunca 0%

        var resposta = metricaService.m1(JanelaMetrica.GLOBAL);

        assertEquals(2, resposta.linhas().size(), "dois formatos, duas linhas — nunca somados (D-36)");
        var multiplaEscolha = resposta.linhas().stream().filter(l -> l.formato() == FormatoBanca.MULTIPLA_ESCOLHA).findFirst().orElseThrow();
        assertEquals(150, multiplaEscolha.total());
        assertTrue(multiplaEscolha.confiavel(), "n=150 >= 100");
        assertEquals(60.0, multiplaEscolha.percentual());

        var certoErrado = resposta.linhas().stream().filter(l -> l.formato() == FormatoBanca.CERTO_ERRADO).findFirst().orElseThrow();
        assertEquals(5, certoErrado.total());
        assertNull(certoErrado.percentual(), "n=5 < 10 devolve nulo, nunca 0%");
    }

    @Test
    void m1_globalSomaDisciplinasPorDisciplinaSepara() {
        var disciplinaA = novaDisciplina();
        var assuntoA = novoAssunto(disciplinaA.getId());
        registrarQuestoes(assuntoA.getId(), HOJE, 50, 100, FormatoBanca.MULTIPLA_ESCOLHA, (short) 50);

        var disciplinaB = novaDisciplina();
        var assuntoB = novoAssunto(disciplinaB.getId());
        registrarQuestoes(assuntoB.getId(), HOJE, 20, 100, FormatoBanca.MULTIPLA_ESCOLHA, (short) 50);

        var porDisciplina = metricaService.m1(JanelaMetrica.POR_DISCIPLINA);
        var linhaA = porDisciplina.linhas().stream().filter(l -> disciplinaA.getId().equals(l.disciplinaId())).findFirst().orElseThrow();
        var linhaB = porDisciplina.linhas().stream().filter(l -> disciplinaB.getId().equals(l.disciplinaId())).findFirst().orElseThrow();
        assertEquals(50.0, linhaA.percentual());
        assertEquals(20.0, linhaB.percentual());

        var global = metricaService.m1(JanelaMetrica.GLOBAL);
        assertEquals(1, global.linhas().size(), "mesmo formato, disciplinas diferentes: uma linha só na janela global");
        assertEquals(200, global.linhas().get(0).total());
        assertEquals(35.0, global.linhas().get(0).percentual());
    }

    @Test
    void m1_simuladoSomaNoMesmoAgregadoDeQuestoes() {
        var disciplina = novaDisciplina();
        var assunto = novoAssunto(disciplina.getId());
        registrarQuestoes(assunto.getId(), HOJE, 50, 100, FormatoBanca.MULTIPLA_ESCOLHA, (short) 50); // 50/100

        simuladoService.registrar(new SimuladoRequest(HOJE, 240, List.of(
            new ResultadoSimuladoRequest(disciplina.getId(), FormatoBanca.MULTIPLA_ESCOLHA, 30, 100)))); // 30/100

        var resposta = metricaService.m1(JanelaMetrica.GLOBAL);
        var linha = resposta.linhas().stream().filter(l -> l.formato() == FormatoBanca.MULTIPLA_ESCOLHA).findFirst().orElseThrow();

        assertEquals(200, linha.total(), "100 da sessão + 100 do simulado, mesmo agregado (docs/SPRINT-8-SIMULADO.md §0/§4)");
        assertEquals(80, linha.acertos());
    }

    @Test
    void m2_serieDeQuestoesComparaPrimeiraComMediaDasSeguintes() {
        var disciplina = novaDisciplina();
        var assunto = novoAssunto(disciplina.getId());
        registrarQuestoes(assunto.getId(), HOJE, 6, 10, FormatoBanca.MULTIPLA_ESCOLHA, (short) 50); // primeira: 60%
        registrarQuestoes(assunto.getId(), HOJE.plusDays(10), 9, 10, FormatoBanca.MULTIPLA_ESCOLHA, (short) 50); // 90%
        registrarQuestoes(assunto.getId(), HOJE.plusDays(20), 8, 10, FormatoBanca.MULTIPLA_ESCOLHA, (short) 50); // 80%

        var resposta = metricaService.m2(assunto.getId());

        var serie = resposta.series().stream().filter(s -> s.tipo() == TipoSessao.QUESTOES).findFirst().orElseThrow();
        assertEquals(60.0, serie.percentualPrimeira());
        assertEquals(85.0, serie.percentualMediaSeguintes(), "média de 90 e 80");
        assertEquals(3, serie.totalObservacoes());
    }

    @Test
    void m2_serieDeRecuperacaoNuncaSomaComQuestoes() {
        var disciplina = novaDisciplina();
        var assunto = novoAssunto(disciplina.getId());
        registrarRecuperacao(assunto.getId(), HOJE, ResultadoSessao.SUCESSO, ResultadoSessao.FALHA); // primeira: 0% (não conseguiu)
        registrarRecuperacao(assunto.getId(), HOJE.plusDays(10), ResultadoSessao.SUCESSO, ResultadoSessao.SUCESSO); // 100%

        var resposta = metricaService.m2(assunto.getId());

        assertTrue(resposta.series().stream().noneMatch(s -> s.tipo() == TipoSessao.QUESTOES), "sem sessão de QUESTOES, sem série de QUESTOES");
        var serie = resposta.series().stream().filter(s -> s.tipo() == TipoSessao.RECUPERACAO).findFirst().orElseThrow();
        assertEquals(0.0, serie.percentualPrimeira());
        assertEquals(100.0, serie.percentualMediaSeguintes());
    }

    @Test
    void m3_objetiva_mediaDesvioRefleteNovaObservacao() {
        var baseline = metricaService.m3().objetiva();
        var disciplina = novaDisciplina();
        var assunto = novoAssunto(disciplina.getId());
        registrarQuestoes(assunto.getId(), HOJE, 6, 10, FormatoBanca.MULTIPLA_ESCOLHA, (short) 80); // previu 80, real 60 -> desvio +20

        var depois = metricaService.m3().objetiva();

        assertEquals(baseline.n() + 1, depois.n());
        var esperado = (baseline.mediaDesvio() * baseline.n() + 20.0) / depois.n();
        assertEquals(esperado, depois.mediaDesvio(), 0.0001);
    }

    @Test
    void m3_subjetiva_contaSuperestimouSubestimouEAcertou() {
        var baseline = metricaService.m3().subjetiva();
        var disciplina = novaDisciplina();
        var superestimou = novoAssunto(disciplina.getId(), 1);
        var subestimou = novoAssunto(disciplina.getId(), 2);
        var acertou = novoAssunto(disciplina.getId(), 3);

        registrarRecuperacao(superestimou.getId(), HOJE, ResultadoSessao.SUCESSO, ResultadoSessao.FALHA); // previu que ia, não foi
        registrarRecuperacao(subestimou.getId(), HOJE, ResultadoSessao.FALHA, ResultadoSessao.SUCESSO); // previu que não ia, foi
        registrarRecuperacao(acertou.getId(), HOJE, ResultadoSessao.SUCESSO, ResultadoSessao.SUCESSO); // previu certo

        var depois = metricaService.m3().subjetiva();

        assertEquals(baseline.superestimou() + 1, depois.superestimou());
        assertEquals(baseline.subestimou() + 1, depois.subestimou());
        assertEquals(baseline.acertouPrevisao() + 1, depois.acertouPrevisao());
        assertEquals(baseline.n() + 3, depois.n());
    }

    @Test
    void m4_aderenciaContaDentroDosTresDiasESeparaOQueFicouFora() {
        var baseline = metricaService.m4();
        var disciplina = novaDisciplina();

        // ESTUDO em HOJE agenda nivel 1, previsto pra HOJE+1 (intervalo_nivel_1 = 1).
        var dentroDoPrazo = novoAssunto(disciplina.getId(), 1);
        sessaoService.registrar(new SessaoRequest(
            dentroDoPrazo.getId(), TipoSessao.ESTUDO, HOJE, 20, null, null, null, null, null, null,
            UUID.randomUUID()));
        registrarRecuperacao(dentroDoPrazo.getId(), HOJE.plusDays(1), ResultadoSessao.SUCESSO, ResultadoSessao.SUCESSO); // diff = 0

        var foraDoPrazo = novoAssunto(disciplina.getId(), 2);
        sessaoService.registrar(new SessaoRequest(
            foraDoPrazo.getId(), TipoSessao.ESTUDO, HOJE, 20, null, null, null, null, null, null,
            UUID.randomUUID()));
        registrarRecuperacao(foraDoPrazo.getId(), HOJE.plusDays(5), ResultadoSessao.SUCESSO, ResultadoSessao.SUCESSO); // diff = 4

        var depois = metricaService.m4();

        assertEquals(baseline.dentroDoPrazo() + 1, depois.dentroDoPrazo());
        assertEquals(baseline.totalCumpridas() + 2, depois.totalCumpridas());
    }
}
