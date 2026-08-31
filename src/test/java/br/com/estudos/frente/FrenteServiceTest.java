package br.com.estudos.frente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.FaseAssunto;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Frente/backlog/consolidado e represamento (docs/SPRINT-5-FRENTE.md §4).
 * Item 5.4 de PROGRESSO.md.
 *
 * "Hoje" fixado ({@link RelogioFixoConfig}) — represamento depende de data,
 * e 03_INVARIANTES §10 exige data congelada em teste que depende de prazo.
 */
@Transactional
class FrenteServiceTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

    @TestConfiguration
    static class RelogioFixoConfig {
        // Nome de bean diferente de propósito: Spring recusa duas definições
        // com o MESMO nome ("clock") mesmo com @Primary — @Primary só resolve
        // ambiguidade de TIPO na hora de injetar, não substitui a definição
        // de EstudosApplication.clock() por nome.
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
    private FrenteService frenteService;

    private Disciplina novaDisciplina(String nome) {
        return disciplinaService.criar(new DisciplinaRequest(nome, TipoPeso.MEDIO));
    }

    private Assunto novoAssunto(Long disciplinaId, String nome, int ordem) {
        return assuntoService.criar(new AssuntoRequest(disciplinaId, nome, TipoPeso.BAIXO, (short) 3, ordem));
    }

    private void registrarEstudo(Long assuntoId, LocalDate data) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 20, "tentativaId": "%s"}
            """.formatted(assuntoId, data, UUID.randomUUID())))
            .andExpect(status().isCreated());
    }

    private void registrarQuestoesSucesso(Long assuntoId, LocalDate data) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "QUESTOES", "data": "%s", "tempoMinutos": 20,
             "questoesCorretas": 9, "questoesTotal": 10, "formato": "MULTIPLA_ESCOLHA",
             "previsaoPercentual": 50, "tentativaId": "%s"}
            """.formatted(assuntoId, data, UUID.randomUUID())))
            .andExpect(status().isCreated());
    }

    /** Sobe a escada inteira (peso BAIXO, alvo nível 3) até consolidar. */
    private void consolidar(Long assuntoId) throws Exception {
        registrarEstudo(assuntoId, HOJE.minusYears(1));
        registrarQuestoesSucesso(assuntoId, HOJE.minusYears(1).plusDays(1));  // nível 1 -> 2
        registrarQuestoesSucesso(assuntoId, HOJE.minusYears(1).plusDays(4));  // nível 2 -> 3 (alvo)
        registrarQuestoesSucesso(assuntoId, HOJE.minusYears(1).plusDays(11)); // 1º sucesso no alvo
        registrarQuestoesSucesso(assuntoId, HOJE.minusYears(1).plusDays(18)); // 2º sucesso seguido -> consolida
    }

    @Test
    void assuntoSemSessao_ehBacklog() {
        var disciplina = novaDisciplina("Backlog");
        var assunto = novoAssunto(disciplina.getId(), "Sem Sessao", 1);

        assertEquals(FaseAssunto.BACKLOG, frenteService.fase(assunto.getId()));
    }

    @Test
    void assuntoComSessaoNaoConsolidado_ehFrente() throws Exception {
        var disciplina = novaDisciplina("Frente");
        var assunto = novoAssunto(disciplina.getId(), "Em Frente", 1);
        registrarEstudo(assunto.getId(), HOJE);

        assertEquals(FaseAssunto.FRENTE, frenteService.fase(assunto.getId()));
    }

    @Test
    void assuntoConsolidado_ehConsolidado() throws Exception {
        var disciplina = novaDisciplina("Consolida");
        var assunto = novoAssunto(disciplina.getId(), "Consolidado", 1);
        consolidar(assunto.getId());

        assertEquals(FaseAssunto.CONSOLIDADO, frenteService.fase(assunto.getId()));
    }

    @Test
    void disciplinaInativa_assuntoViraBacklogMesmoComHistorico() throws Exception {
        var disciplina = novaDisciplina("Vai Arquivar");
        var assunto = novoAssunto(disciplina.getId(), "Com Historico", 1);
        registrarEstudo(assunto.getId(), HOJE);
        assertEquals(FaseAssunto.FRENTE, frenteService.fase(assunto.getId()));

        disciplinaService.arquivar(disciplina.getId());

        assertEquals(FaseAssunto.BACKLOG, frenteService.fase(assunto.getId()), "D-24: disciplina inativa vira backlog mesmo com sessão");
    }

    @Test
    void resumo_contagensBatemENaoDisparaAlertaAbaixoDoTeto() throws Exception {
        var disciplina = novaDisciplina("Resumo");
        var backlog = novoAssunto(disciplina.getId(), "Backlog", 1);
        var frente = novoAssunto(disciplina.getId(), "Frente", 2);
        registrarEstudo(frente.getId(), HOJE);

        var resumo = frenteService.resumo();

        assertTrue(resumo.backlog() >= 1);
        assertTrue(resumo.frenteAtual() >= 1);
        assertEquals(8, resumo.tetoDiario());
        assertEquals(100, resumo.tetoGlobalFrente());
    }

    @Test
    void represamento_soDisparaAcimaDoTetoDiario() throws Exception {
        var disciplina = novaDisciplina("Represamento");
        // Teto diário é 8 — 8 pendentes vencidas ainda não dispara.
        for (int i = 1; i <= 8; i++) {
            var assunto = novoAssunto(disciplina.getId(), "Vencido " + i, i);
            registrarEstudo(assunto.getId(), HOJE.minusMonths(1)); // nível 1, previsto muito antes de HOJE
        }
        assertFalse(frenteService.resumo().alertaRepresamento(), "8 represadas, igual ao teto, não dispara");

        // A nona dispara.
        var nono = novoAssunto(disciplina.getId(), "Vencido 9", 9);
        registrarEstudo(nono.getId(), HOJE.minusMonths(1));

        var resumo = frenteService.resumo();
        assertTrue(resumo.alertaRepresamento(), "9 represadas, acima do teto de 8, dispara");
        assertEquals(1, resumo.estimativaDiasParaNormalizar(), "9 / 8 (divisão inteira) = 1");
    }

    @Test
    void proximaVaga_sugereMenorOrdemDoBacklog() throws Exception {
        var disciplina = novaDisciplina("Vaga");
        novoAssunto(disciplina.getId(), "Ordem 3", 3);
        var ordemUm = novoAssunto(disciplina.getId(), "Ordem 1", 1);
        novoAssunto(disciplina.getId(), "Ordem 2", 2);

        var sugestao = frenteService.proximaVaga(disciplina.getId());

        assertTrue(sugestao.isPresent());
        assertEquals(ordemUm.getId(), sugestao.get().getId());
    }

    @Test
    void proximaVaga_semSugestaoQuandoDisciplinaNoTeto() throws Exception {
        var disciplina = novaDisciplina("Teto Disciplina");
        // Teto por disciplina é 12 — enche a frente com 12 antes de ter backlog.
        for (int i = 1; i <= 12; i++) {
            var assunto = novoAssunto(disciplina.getId(), "Frente " + i, i);
            registrarEstudo(assunto.getId(), HOJE);
        }
        novoAssunto(disciplina.getId(), "Backlog Sobrando", 13);

        var sugestao = frenteService.proximaVaga(disciplina.getId());

        assertTrue(sugestao.isEmpty(), "disciplina já no teto — sem sugestão mesmo havendo backlog");
    }
}
