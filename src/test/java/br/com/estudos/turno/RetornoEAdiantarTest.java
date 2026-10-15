package br.com.estudos.turno;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.revisao.RevisaoRepository;
import br.com.estudos.segmento.SegmentoRequest;
import br.com.estudos.segmento.SegmentoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Mensagem de retorno (02_JORNADAS J-4) e "Quer adiantar?" (§4.3) no plano do
 * turno. docs/SPRINT-17-USO-REAL.md §6.5.
 *
 * "Hoje" bem à frente de qualquer data usada pelos outros testes, para que as
 * sessões daqui sejam as mais recentes do banco e nenhuma revisão alheia
 * caia no futuro deste relógio.
 */
@Transactional
class RetornoEAdiantarTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.of(2030, 1, 15);

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired private DisciplinaService disciplinaService;
    @Autowired private AssuntoService assuntoService;
    @Autowired private SegmentoService segmentoService;
    @Autowired private RevisaoRepository revisaoRepository;
    @Autowired private TurnoService turnoService;

    private Assunto novoAssunto(String nome) {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina " + nome, TipoPeso.MEDIO));
        return assuntoService.criar(new AssuntoRequest(disciplina.getId(), nome, TipoPeso.BAIXO, (short) 3, 1, null));
    }

    private void registrar(String corpo) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isCreated());
    }

    private void estudar(Long assuntoId, LocalDate data, Long segmentoId) throws Exception {
        var segmento = segmentoId == null ? "" : ", \"segmentoId\": " + segmentoId;
        registrar("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 50, "tentativaId": "%s"%s}
            """.formatted(assuntoId, data, UUID.randomUUID(), segmento));
    }

    private void questoesSucesso(Long assuntoId, LocalDate data) throws Exception {
        registrar("""
            {"assuntoId": %d, "tipo": "QUESTOES", "data": "%s", "tempoMinutos": 20,
             "questoesCorretas": 9, "questoesTotal": 10, "formato": "MULTIPLA_ESCOLHA",
             "previsaoPercentual": 50, "tentativaId": "%s"}
            """.formatted(assuntoId, data, UUID.randomUUID()));
    }

    @Test
    void diasSemEstudarContaDesdeASessaoMaisRecente() throws Exception {
        var assunto = novoAssunto("Retorno Fora");
        estudar(assunto.getId(), HOJE.minusDays(16), null);

        assertEquals(16L, turnoService.plano().diasSemEstudar(), "J-4: \"você esteve fora 16 dias\"");

        estudar(assunto.getId(), HOJE, null);
        assertEquals(0L, turnoService.plano().diasSemEstudar(), "registrou hoje: a mensagem some sozinha");
    }

    @Test
    void drenarMaisAumentaAFilaSoQuandoPedido() throws Exception {
        for (int i = 0; i < 10; i++) {
            estudar(novoAssunto("Retorno Represado " + i).getId(), HOJE.minusDays(5), null); // vence em HOJE-4
        }
        var vencidas = revisaoRepository.listarVencidasPorAtraso(HOJE).size();

        var plano = turnoService.plano();
        assertEquals(Math.min(vencidas, 8), plano.filaRecuperacao().size(), "sem pedido, o teto diário (8) vale");
        assertEquals(vencidas - plano.filaRecuperacao().size(), plano.vencidasAlemDoTeto());
        assertTrue(plano.vencidasAlemDoTeto() >= 2);

        var comExtra = turnoService.plano(8);
        assertEquals(Math.min(vencidas, 16), comExtra.filaRecuperacao().size(), "§4.3: drenar mais é explícito");
        assertTrue(plano.represado() >= 10);
    }

    @Test
    void antecipaveisSoDentroDaJanela() throws Exception {
        // BAIXO, nível 3 = 7 dias, tolerância 20% = 1 dia.
        var dentro = novoAssunto("Retorno Antecipa Dentro");
        estudar(dentro.getId(), HOJE.minusDays(10), null);
        questoesSucesso(dentro.getId(), HOJE.minusDays(9));  // nível 1 -> 2, prevista HOJE-6
        questoesSucesso(dentro.getId(), HOJE.minusDays(6));  // nível 2 -> 3, prevista HOJE+1: janela abre hoje

        var fora = novoAssunto("Retorno Antecipa Fora");
        estudar(fora.getId(), HOJE.minusDays(8), null);
        questoesSucesso(fora.getId(), HOJE.minusDays(7));    // prevista HOJE-4
        questoesSucesso(fora.getId(), HOJE.minusDays(4));    // prevista HOJE+3: janela só abre em HOJE+2

        var antecipaveis = turnoService.plano().antecipaveis();
        assertTrue(antecipaveis.stream().anyMatch(a -> a.assuntoId().equals(dentro.getId())),
            "01_DOMINIO §5.4: dentro da janela, antecipar cumpre a revisão");
        assertFalse(antecipaveis.stream().anyMatch(a -> a.assuntoId().equals(fora.getId())),
            "fora da janela não é oferecida como antecipar");
        var item = antecipaveis.stream().filter(a -> a.assuntoId().equals(dentro.getId())).findFirst().orElseThrow();
        assertEquals(-1, item.diasAtraso(), "um dia antes do previsto");
    }

    @Test
    void outroBlocoEOSegmentoSeguinteDoMesmoAssunto() throws Exception {
        var assunto = novoAssunto("Retorno Outro Bloco");
        var s1 = segmentoService.criarOuAtualizar(new SegmentoRequest(assunto.getId(), "rt-" + UUID.randomUUID(), 1, "https://x/1", 1, 15, 60));
        var s2 = segmentoService.criarOuAtualizar(new SegmentoRequest(assunto.getId(), "rt-" + UUID.randomUUID(), 2, "https://x/2", 16, 30, 60));
        var s3 = segmentoService.criarOuAtualizar(new SegmentoRequest(assunto.getId(), "rt-" + UUID.randomUUID(), 3, "https://x/3", 31, 45, 60));
        estudar(assunto.getId(), HOJE, s1.getId());

        var plano = turnoService.plano();
        assertEquals(s2.getId(), plano.segmentoSugerido().id());
        assertEquals(s3.getId(), plano.proximoBloco().id(), "§4.3: outro bloco = o segmento seguinte não lido");
        assertEquals(3, plano.proximoBloco().total());
    }
}
