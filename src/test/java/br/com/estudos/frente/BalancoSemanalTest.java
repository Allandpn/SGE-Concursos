package br.com.estudos.frente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
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
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Balanço semanal — D-35 (sugestão de divisão) e "a frente está saudável?"
 * de 02_JORNADAS J-3. docs/SPRINT-17-USO-REAL.md §6.5.
 *
 * Testes que gravam de verdade deixam assuntos e sessões no banco; por isso as
 * contagens da semana são conferidas por diferença (antes × depois), e a lista
 * de sugestões por presença do assunto do teste, não por igualdade.
 */
@Transactional
class BalancoSemanalTest extends IntegracaoTestBase {

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
    @Autowired private AssuntoService assuntoService;
    @Autowired private FrenteService frenteService;

    private Assunto novoAssunto(String nome) {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina " + nome, TipoPeso.MEDIO));
        return assuntoService.criar(new AssuntoRequest(disciplina.getId(), nome, TipoPeso.BAIXO, (short) 3, 1, null));
    }

    private void registrar(String corpo) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isCreated());
    }

    private void estudar(Long assuntoId, LocalDate data) throws Exception {
        registrar("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 50, "tentativaId": "%s"}
            """.formatted(assuntoId, data, UUID.randomUUID()));
    }

    private void recuperar(Long assuntoId, LocalDate data, String resultado) throws Exception {
        registrar("""
            {"assuntoId": %d, "tipo": "RECUPERACAO", "data": "%s", "tempoMinutos": 8,
             "previsaoReconstrucao": "SUCESSO", "resultado": "%s", "tentativaId": "%s"}
            """.formatted(assuntoId, data, resultado, UUID.randomUUID()));
    }

    private void questoes(Long assuntoId, LocalDate data, int corretas, int total) throws Exception {
        registrar("""
            {"assuntoId": %d, "tipo": "QUESTOES", "data": "%s", "tempoMinutos": 20,
             "questoesCorretas": %d, "questoesTotal": %d, "formato": "MULTIPLA_ESCOLHA",
             "previsaoPercentual": 50, "tentativaId": "%s"}
            """.formatted(assuntoId, data, corretas, total, UUID.randomUUID()));
    }

    private Optional<SugestaoDivisaoResponse> sugestao(Long assuntoId) {
        return frenteService.sugestoesDeDivisao().stream().filter(s -> s.assuntoId().equals(assuntoId)).findFirst();
    }

    @Test
    void quatroParciaisSeguidosSugeremDivisao() throws Exception {
        var assunto = novoAssunto("Divisao Quatro");
        estudar(assunto.getId(), HOJE.minusDays(20));
        for (int i = 3; i >= 1; i--) {
            recuperar(assunto.getId(), HOJE.minusDays(i * 3L), "PARCIAL");
        }
        assertFalse(sugestao(assunto.getId()).isPresent(), "3 parciais ainda não bastam (partida 4)");

        recuperar(assunto.getId(), HOJE, "PARCIAL");
        var sugestao = sugestao(assunto.getId()).orElseThrow(() -> new AssertionError("D-35: 4 parciais seguidos sugerem dividir"));
        assertEquals(4, sugestao.parciaisSeguidos());
        assertEquals("Divisao Quatro", sugestao.nome());
    }

    @Test
    void sucessoNoMeioZeraASequencia() throws Exception {
        var assunto = novoAssunto("Divisao Interrompida");
        estudar(assunto.getId(), HOJE.minusDays(30));
        recuperar(assunto.getId(), HOJE.minusDays(25), "PARCIAL");
        recuperar(assunto.getId(), HOJE.minusDays(20), "PARCIAL");
        recuperar(assunto.getId(), HOJE.minusDays(15), "SUCESSO");
        recuperar(assunto.getId(), HOJE.minusDays(10), "PARCIAL");
        recuperar(assunto.getId(), HOJE.minusDays(5), "PARCIAL");
        recuperar(assunto.getId(), HOJE.minusDays(2), "PARCIAL");

        assertFalse(sugestao(assunto.getId()).isPresent(), "só conta seguidos a partir do mais recente: 3, não 5");
    }

    @Test
    void loteAbaixoDoMinimoNaoConta() throws Exception {
        var assunto = novoAssunto("Divisao Lote Pequeno");
        estudar(assunto.getId(), HOJE.minusDays(30));
        recuperar(assunto.getId(), HOJE.minusDays(20), "PARCIAL");
        recuperar(assunto.getId(), HOJE.minusDays(15), "PARCIAL");
        recuperar(assunto.getId(), HOJE.minusDays(10), "PARCIAL");
        questoes(assunto.getId(), HOJE.minusDays(5), 3, 3); // SUCESSO, mas 3 < lote mínimo (5): não classifica
        recuperar(assunto.getId(), HOJE.minusDays(1), "PARCIAL");

        assertEquals(4, sugestao(assunto.getId()).orElseThrow().parciaisSeguidos(),
            "01_DOMINIO §3.2.1: lote abaixo do mínimo não interrompe nem conta");
    }

    /** Sobe a escada de um assunto BAIXO (alvo 3) e consolida 18 dias depois do estudo. */
    private void consolidarComEstudoEm(Long assuntoId, LocalDate estudo) throws Exception {
        estudar(assuntoId, estudo);
        questoes(assuntoId, estudo.plusDays(1), 9, 10);
        questoes(assuntoId, estudo.plusDays(4), 9, 10);
        questoes(assuntoId, estudo.plusDays(11), 9, 10);
        questoes(assuntoId, estudo.plusDays(18), 9, 10); // 2º SUCESSO seguido no alvo: consolida
    }

    @Test
    void frenteDaSemana() throws Exception {
        var antes = frenteService.resumo();

        var entrouNaSemana = novoAssunto("Semana Entrou");
        estudar(entrouNaSemana.getId(), HOJE.minusDays(2));
        var entrouAntes = novoAssunto("Semana Entrou Antes");
        estudar(entrouAntes.getId(), HOJE.minusDays(10));

        consolidarComEstudoEm(novoAssunto("Semana Consolidou").getId(), HOJE.minusDays(20)); // consolida em HOJE-2
        consolidarComEstudoEm(novoAssunto("Semana Consolidou Antes").getId(), HOJE.minusDays(30)); // HOJE-12

        var depois = frenteService.resumo();
        assertEquals(antes.entraramNaSemana() + 1, depois.entraramNaSemana(),
            "só o assunto cuja primeira sessão caiu nos últimos 7 dias");
        assertEquals(antes.consolidaramNaSemana() + 1, depois.consolidaramNaSemana(),
            "só a consolidação que aconteceu nos últimos 7 dias");
        assertTrue(depois.consolidados() >= antes.consolidados() + 2);
    }
}
