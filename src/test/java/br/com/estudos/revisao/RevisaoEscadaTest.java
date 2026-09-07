package br.com.estudos.revisao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.SituacaoRevisao;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Agendamento, cumprimento e roteamento da escada — D-07/D-10/D-11/D-17,
 * janela de tolerância (docs/SPRINT-4-ESCADA.md §5). Item 4.6 de PROGRESSO.md.
 *
 * Peso BAIXO em todos os fixtures: nível-alvo 3 (não 6), escada curta o
 * bastante para testar consolidação sem inflar o teste. @Transactional: só
 * confere o estado de Revisao dentro da mesma transação do teste, nenhum
 * destes cenários precisa observar commit real entre chamadas.
 */
@Transactional
class RevisaoEscadaTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Autowired
    private RevisaoRepository revisaoRepository;

    private Assunto novoAssunto(String nome) {
        Disciplina disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina " + nome, TipoPeso.MEDIO));
        return assuntoService.criar(new AssuntoRequest(disciplina.getId(), nome, TipoPeso.BAIXO, (short) 3, 1, null));
    }

    private void registrar(String corpo) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isCreated());
    }

    private String estudo(Long assuntoId, LocalDate data) {
        return """
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 20, "tentativaId": "%s"}
            """.formatted(assuntoId, data, UUID.randomUUID());
    }

    private String questoes(Long assuntoId, LocalDate data, int corretas, int total) {
        return """
            {"assuntoId": %d, "tipo": "QUESTOES", "data": "%s", "tempoMinutos": 20,
             "questoesCorretas": %d, "questoesTotal": %d, "formato": "MULTIPLA_ESCOLHA",
             "previsaoPercentual": 50, "tentativaId": "%s"}
            """.formatted(assuntoId, data, corretas, total, UUID.randomUUID());
    }

    private Revisao pendente(Long assuntoId) {
        return revisaoRepository.findByAssuntoIdAndSituacao(assuntoId, SituacaoRevisao.PENDENTE).orElseThrow();
    }

    @Test
    void estudo_agendaPrimeiraPendenteSemDuplicar() throws Exception {
        var assunto = novoAssunto("Estudo Agenda");

        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));
        var primeira = pendente(assunto.getId());
        assertEquals(1, primeira.getNivel());
        assertEquals(LocalDate.of(2026, 1, 2), primeira.getDataPrevista());

        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));
        assertEquals(1, revisaoRepository.findByAssuntoId(assunto.getId()).size(), "ESTUDO de novo não duplica a pendente");
    }

    @Test
    void sucesso_sobeNivelComIntervaloDaEscada() throws Exception {
        var assunto = novoAssunto("Sucesso Sobe");
        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));

        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 2), 9, 10)); // 90% -> SUCESSO

        var proxima = pendente(assunto.getId());
        assertEquals(2, proxima.getNivel());
        assertEquals(LocalDate.of(2026, 1, 5), proxima.getDataPrevista()); // +3 dias (nível 2)
    }

    @Test
    void parcial_repeteNivel() throws Exception {
        var assunto = novoAssunto("Parcial Repete");
        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 2), 9, 10)); // SUCESSO -> nível 2

        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 5), 6, 10)); // 60% -> PARCIAL

        var proxima = pendente(assunto.getId());
        assertEquals(2, proxima.getNivel(), "PARCIAL repete o mesmo nível");
    }

    @Test
    void falha_regrideNivelComPisoUm() throws Exception {
        var assunto = novoAssunto("Falha Regride");
        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));

        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 2), 3, 10)); // 30% -> FALHA no nível 1

        var proxima = pendente(assunto.getId());
        assertEquals(1, proxima.getNivel(), "piso é 1, não regride abaixo disso");
    }

    @Test
    void recuperacaoEspontanea_semPendente_criaRoteada() throws Exception {
        var assunto = novoAssunto("Espontanea");

        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 1), 9, 10)); // SUCESSO, sem ESTUDO antes

        var proxima = pendente(assunto.getId());
        assertEquals(2, proxima.getNivel(), "roteia a partir do nível 1 implícito");
        assertEquals(null, proxima.getSessaoCumpriu(), "não havia pendente para marcar cumprida");
    }

    @Test
    void foraDaJanela_naoAlteraRevisaoPendente() throws Exception {
        var assunto = novoAssunto("Fora Janela");
        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 2), 9, 10)); // nível 2, previsto 01-05
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 5), 9, 10)); // nível 3, previsto 01-12 (+7)

        var antes = pendente(assunto.getId());
        assertEquals(3, antes.getNivel());
        assertEquals(LocalDate.of(2026, 1, 12), antes.getDataPrevista());

        // Tolerância no nível 3 (intervalo 7) = 1 dia. Tentando 2 dias antes: fora da janela.
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 10), 9, 10));

        var depois = pendente(assunto.getId());
        assertEquals(antes.getId(), depois.getId(), "revisão pendente continua a mesma");
        assertEquals(SituacaoRevisao.PENDENTE, depois.getSituacao());
        assertTrue(depois.getSessaoCumpriu() == null, "sessão fora da janela não cumpre a revisão");
    }

    @Test
    void loteAbaixoDoMinimo_naoAlteraRevisaoPendente() throws Exception {
        var assunto = novoAssunto("Lote Pequeno");
        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));
        var antes = pendente(assunto.getId());

        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 2), 3, 3)); // lote de 3 < mínimo de 5 (D-09)

        var depois = pendente(assunto.getId());
        assertEquals(antes.getId(), depois.getId(), "revisão pendente continua a mesma");
        assertEquals(1, depois.getNivel(), "lote abaixo do mínimo não move a escada");
        assertTrue(depois.getSessaoCumpriu() == null, "lote abaixo do mínimo não cumpre revisão");
    }

    @Test
    void loteAbaixoDoMinimo_semPendente_naoCriaRevisao() throws Exception {
        var assunto = novoAssunto("Lote Pequeno Espontaneo");

        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 1), 3, 3)); // sem ESTUDO antes, lote pequeno

        assertTrue(revisaoRepository.findByAssuntoId(assunto.getId()).isEmpty(),
            "lote abaixo do mínimo não cria revisão nem roteada (D-09)");
    }

    @Test
    void consolidacao_doisSucessosNoAlvo_usaIntervaloDeManutencao() throws Exception {
        var assunto = novoAssunto("Consolida");
        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 2), 9, 10));  // nível 1 -> 2
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 5), 9, 10));  // nível 2 -> 3 (alvo, BAIXO)
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 12), 9, 10)); // 1º SUCESSO no alvo — ainda não consolida

        var primeiraNoAlvo = pendente(assunto.getId());
        assertEquals(3, primeiraNoAlvo.getNivel());
        assertEquals(LocalDate.of(2026, 1, 19), primeiraNoAlvo.getDataPrevista(), "ainda intervalo de escada (+7), não manutenção");

        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 19), 9, 10)); // 2º SUCESSO seguido no alvo — consolida

        var manutencao = pendente(assunto.getId());
        assertEquals(3, manutencao.getNivel());
        assertEquals(LocalDate.of(2026, 6, 18), manutencao.getDataPrevista(), "+150 dias (intervalo de manutenção)");
    }

    @Test
    void falhaConsolidado_voltaAoNivelAlvoNaoAbaixo() throws Exception {
        var assunto = novoAssunto("Falha Consolidado");
        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 2), 9, 10));
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 5), 9, 10));
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 12), 9, 10)); // 1º no alvo
        registrar(questoes(assunto.getId(), LocalDate.of(2026, 1, 19), 9, 10)); // 2º no alvo -> consolidado

        var manutencao = pendente(assunto.getId());
        registrar(questoes(assunto.getId(), manutencao.getDataPrevista(), 3, 10)); // FALHA em manutenção

        var proxima = pendente(assunto.getId());
        assertEquals(3, proxima.getNivel(), "D-11: sai de consolidado, mas fica no nível-alvo, não abaixo");
        assertEquals(manutencao.getDataPrevista().plusDays(7), proxima.getDataPrevista(), "volta ao intervalo de escada do nível-alvo");
    }

    @Test
    void arquivarAssunto_cancelaRevisaoPendente() throws Exception {
        var assunto = novoAssunto("Arquivar Assunto");
        registrar(estudo(assunto.getId(), LocalDate.of(2026, 1, 1)));
        var idPendente = pendente(assunto.getId()).getId();

        assuntoService.arquivar(assunto.getId());

        var revisao = revisaoRepository.findById(idPendente).orElseThrow();
        assertEquals(SituacaoRevisao.CANCELADA, revisao.getSituacao());
        assertEquals(false, assuntoService.buscar(assunto.getId()).isAtivo(),
            "o próprio arquivamento não pode se perder no clearAutomatically do cancelamento");
    }

    @Test
    void arquivarDisciplina_cancelaPendentesDeTodosOsAssuntos() throws Exception {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Arquivar Todos", TipoPeso.MEDIO));
        var assunto1 = assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Assunto Um", TipoPeso.BAIXO, (short) 3, 1, null));
        var assunto2 = assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Assunto Dois", TipoPeso.BAIXO, (short) 3, 2, null));
        registrar(estudo(assunto1.getId(), LocalDate.of(2026, 1, 1)));
        registrar(estudo(assunto2.getId(), LocalDate.of(2026, 1, 1)));

        disciplinaService.arquivar(disciplina.getId());

        assertEquals(SituacaoRevisao.CANCELADA, pendenteOuCancelada(assunto1.getId()).getSituacao());
        assertEquals(SituacaoRevisao.CANCELADA, pendenteOuCancelada(assunto2.getId()).getSituacao());
        assertEquals(false, disciplinaService.buscar(disciplina.getId()).isAtivo(),
            "o próprio arquivamento não pode se perder no clearAutomatically do cancelamento");
    }

    private Revisao pendenteOuCancelada(Long assuntoId) {
        return revisaoRepository.findByAssuntoId(assuntoId).get(0);
    }
}
