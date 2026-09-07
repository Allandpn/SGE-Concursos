package br.com.estudos.turno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
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

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.frente.FrenteService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.parametro.ParametroRepository;

/**
 * Plano de turno — fila de recuperação e bloco de conteúdo
 * (docs/SPRINT-6-TURNO.md §4). Item 6.4 de PROGRESSO.md.
 *
 * "Hoje" fixado — mesmo motivo de FrenteServiceTest (03_INVARIANTES §10).
 */
@Transactional
class TurnoServiceTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

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
    private ParametroRepository parametroRepository;

    @Autowired
    private TurnoService turnoService;

    @Autowired
    private FrenteService frenteService;

    private void registrarEstudo(Long assuntoId, LocalDate data) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 20, "tentativaId": "%s"}
            """.formatted(assuntoId, data, UUID.randomUUID())))
            .andExpect(status().isCreated());
    }

    @Test
    void semRevisaoVencida_filaVazia() {
        var plano = turnoService.plano();
        assertTrue(plano.filaRecuperacao().isEmpty());
    }

    @Test
    void filaOrdenadaPorAtrasoECortadaNoTetoDiario() throws Exception {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Fila", TipoPeso.MEDIO));
        // 9 assuntos vencidos, cada um estudado num dia diferente — o teto diário é 8.
        for (int i = 1; i <= 9; i++) {
            var assunto = assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Assunto " + i, TipoPeso.BAIXO, (short) 3, i, null));
            registrarEstudo(assunto.getId(), HOJE.minusMonths(1).minusDays(i)); // quanto maior i, mais atrasado
        }

        var fila = turnoService.plano().filaRecuperacao();

        assertEquals(8, fila.size(), "teto diário corta em 8, mesmo com 9 vencidas");
        assertEquals("Assunto 9", fila.get(0).nome(), "a mais atrasada (i=9) vem primeiro");
        for (int i = 0; i < fila.size() - 1; i++) {
            assertTrue(fila.get(i).diasAtraso() >= fila.get(i + 1).diasAtraso(), "ordem decrescente de atraso");
        }
    }

    @Test
    void blocoDeConteudoSugeridoQuandoHaVaga() throws Exception {
        // Não assume que este é o ÚNICO candidato: outros testes não-transacionais
        // (ImportacaoAssuntoTest etc.) commitam disciplina/assunto de verdade, e
        // continuam no banco durante a suíte inteira — qualquer um deles com
        // backlog é um candidato tão válido quanto o daqui. O que se testa é a
        // fiação TurnoService -> FrenteService, não qual candidato específico
        // vence (isso já é coberto, determinístico, em FrenteServiceTest).
        disciplinaService.criar(new DisciplinaRequest("Vaga", TipoPeso.MEDIO));

        var plano = turnoService.plano();

        assertNotNull(plano.blocoConteudo(), "havendo vaga em algum lugar, o plano sugere um bloco de conteúdo");
        assertEquals(frenteService.proximaSugestaoDeConteudo().orElseThrow().getId(), plano.blocoConteudo().id());
    }

    @Test
    void blocoDeConteudoVazioQuandoFrenteNoTetoGlobal() throws Exception {
        // Baixa o teto global pra 1, só pra não precisar criar 100 assuntos no teste.
        var tetoGlobal = parametroRepository.findById("teto_global_frente").orElseThrow();
        tetoGlobal.setValor("1");
        parametroRepository.saveAndFlush(tetoGlobal);

        var disciplina = disciplinaService.criar(new DisciplinaRequest("Teto Global", TipoPeso.MEDIO));
        var naFrente = assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Ja Na Frente", TipoPeso.BAIXO, (short) 3, 1, null));
        registrarEstudo(naFrente.getId(), HOJE);
        assuntoService.criar(new AssuntoRequest(disciplina.getId(), "No Backlog", TipoPeso.BAIXO, (short) 3, 2, null));

        var plano = turnoService.plano();

        assertNull(plano.blocoConteudo(), "frente já no teto global (1) — não sugere assunto novo");
    }
}
