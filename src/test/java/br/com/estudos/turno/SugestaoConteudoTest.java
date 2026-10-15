package br.com.estudos.turno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.segmento.SegmentoRequest;
import br.com.estudos.segmento.SegmentoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.parametro.ParametroRepository;

/**
 * O que o bloco de conteúdo do turno sugere — D-56 (represamento), D-57
 * (rodízio entre disciplinas) e D-29 (duração de bloco parametrizada).
 * docs/SPRINT-17-USO-REAL.md §2–§4.
 *
 * Outros testes gravam de verdade (sem @Transactional) e deixam disciplinas e
 * revisões vencidas na base; cada teste aqui arquiva as disciplinas ativas
 * antes de começar (D-17 cancela as pendentes delas), dentro da própria
 * transação, para a sugestão depender só do que o teste cria.
 */
@Transactional
class SugestaoConteudoTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.of(2026, 6, 15);

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired private DisciplinaService disciplinaService;
    @Autowired private DisciplinaRepository disciplinaRepository;
    @Autowired private AssuntoService assuntoService;
    @Autowired private SegmentoService segmentoService;
    @Autowired private ParametroRepository parametroRepository;
    @Autowired private TurnoService turnoService;

    @BeforeEach
    void baseLimpa() {
        disciplinaRepository.findByAtivoTrue().forEach(d -> disciplinaService.arquivar(d.getId()));
    }

    private Disciplina disciplina(String nome) {
        return disciplinaService.criar(new DisciplinaRequest("Sugestao " + nome + " " + UUID.randomUUID(), TipoPeso.MEDIO));
    }

    private Assunto assunto(Disciplina disciplina, String nome, int ordem) {
        return assuntoService.criar(new AssuntoRequest(disciplina.getId(), nome, TipoPeso.MEDIO, (short) 3, ordem, null));
    }

    private void estudar(Long assuntoId, Long segmentoId, LocalDate data) throws Exception {
        var segmento = segmentoId == null ? "" : ", \"segmentoId\": " + segmentoId;
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 50, "tentativaId": "%s"%s}
            """.formatted(assuntoId, data, UUID.randomUUID(), segmento)))
            .andExpect(status().isCreated());
    }

    private void tetoDiario(String valor) {
        var teto = parametroRepository.findById("teto_diario_recuperacoes").orElseThrow();
        teto.setValor(valor);
        parametroRepository.saveAndFlush(teto);
    }

    @Test
    void represamento_naoSugereAssuntoNovo() throws Exception {
        var d = disciplina("Represada");
        estudar(assunto(d, "Represado Um", 1).getId(), null, HOJE.minusDays(10));
        estudar(assunto(d, "Represado Dois", 2).getId(), null, HOJE.minusDays(10));
        assunto(d, "Backlog Disponivel", 3);
        tetoDiario("1"); // duas revisões vencidas > teto de 1

        var plano = turnoService.plano();

        assertTrue(plano.alertaRepresamento());
        assertNull(plano.blocoConteudo(), "D-56: com o alerta ligado, nenhum assunto novo do backlog");
    }

    @Test
    void represamento_continuaLeituraEmCurso() throws Exception {
        var d = disciplina("Represada Leitura");
        estudar(assunto(d, "Represado Tres", 1).getId(), null, HOJE.minusDays(10));
        estudar(assunto(d, "Represado Quatro", 2).getId(), null, HOJE.minusDays(10));
        var lendo = assunto(d, "Em Leitura", 3);
        var s1 = segmentoService.criarOuAtualizar(new SegmentoRequest(
            lendo.getId(), "seg-" + UUID.randomUUID(), 1, "Apostila.pdf", 1, 20, 40));
        var s2 = segmentoService.criarOuAtualizar(new SegmentoRequest(
            lendo.getId(), "seg-" + UUID.randomUUID(), 2, "Apostila.pdf", 21, 40, 40));
        estudar(lendo.getId(), s1.getId(), HOJE.minusDays(1));
        tetoDiario("1");

        var plano = turnoService.plano();

        assertTrue(plano.alertaRepresamento());
        assertTrue(plano.blocoContinuacao(), "D-56: terminar a leitura não abre vaga, segue sugerido");
        assertEquals(s2.getId(), plano.segmentoSugerido().id());
    }

    @Test
    void semRepresamento_alertaDesligado() throws Exception {
        assunto(disciplina("Tranquila"), "Tranquilo", 1);

        var plano = turnoService.plano();

        assertFalse(plano.alertaRepresamento());
    }

    @Test
    void assuntoNovoVemDaDisciplinaComMenosNaFrente() throws Exception {
        // A tem id menor — na ordem antiga, venceria sempre até encher o teto dela.
        var a = disciplina("A Cheia");
        var b = disciplina("B Vazia");
        estudar(assunto(a, "A Estudado Um", 1).getId(), null, HOJE);
        estudar(assunto(a, "A Estudado Dois", 2).getId(), null, HOJE);
        assunto(a, "A Backlog", 3);
        var esperado = assunto(b, "B Backlog", 1);

        var plano = turnoService.plano();

        assertEquals(esperado.getId(), plano.blocoConteudo().id(), "D-57: B tem 0 na frente, A tem 2");
    }

    @Test
    void empateNaFrente_ficaComAOrdemDasDisciplinas() throws Exception {
        var a = disciplina("A Empate");
        var b = disciplina("B Empate");
        var esperado = assunto(a, "A Primeiro", 1);
        assunto(b, "B Primeiro", 1);

        var plano = turnoService.plano();

        assertEquals(esperado.getId(), plano.blocoConteudo().id(), "D-57: 0 a 0, desempata pelo id da disciplina");
    }

    @Test
    void duracaoDeBloco_ehParametroAlteravelSemReinicio() throws Exception {
        mockMvc.perform(patch("/api/parametros/duracao_bloco_min")
                .contentType(MediaType.APPLICATION_JSON).content("{\"valor\": \"65\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/parametros"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.chave == 'duracao_bloco_min')].valor").value("65"));
    }
}
