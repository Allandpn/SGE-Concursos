package br.com.estudos.foco;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.foco.FaseFoco.Especie;
import br.com.estudos.foco.FaseFoco.Motivo;
import br.com.estudos.sessao.SessaoRequest;
import br.com.estudos.sessao.SessaoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Pomodoro (docs/SPRINT-29-POMODORO.md §7): D-98 a D-105 contra Postgres real, com relógio controlado. Sem @Transactional: a unicidade
 * e o consumo dos focos precisam de commit de verdade.
 */
@Import(FocoServiceTest.Config.class)
class FocoServiceTest extends IntegracaoTestBase {

    static final Instant T0 = Instant.parse("2026-10-30T15:00:00Z");
    static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    static final RelogioMutavel RELOGIO = new RelogioMutavel(T0, ZONA);

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        Clock relogioMutavel() {
            return RELOGIO;
        }
    }

    @Autowired private FocoService foco;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DisciplinaService disciplinas;
    @Autowired private AssuntoService assuntos;
    @Autowired private SessaoService sessoes;

    @BeforeEach
    void limpar() {
        jdbc.update("DELETE FROM fase_foco");
        jdbc.update("UPDATE parametro SET valor = '25' WHERE chave = 'pomodoro_foco_min'");
        jdbc.update("UPDATE parametro SET valor = '5' WHERE chave = 'pomodoro_pausa_curta_min'");
        jdbc.update("UPDATE parametro SET valor = '15' WHERE chave = 'pomodoro_pausa_longa_min'");
        jdbc.update("UPDATE parametro SET valor = '4' WHERE chave = 'pomodoro_focos_ate_longa'");
        RELOGIO.definir(T0);
    }

    private long novoAssunto() {
        var d = disciplinas.criar(new DisciplinaRequest("Disciplina Foco " + System.nanoTime(), TipoPeso.MEDIO));
        return assuntos.criar(new AssuntoRequest(d.getId(), "Assunto Foco " + System.nanoTime(), TipoPeso.MEDIO, (short) 3, 1, null)).getId();
    }

    private FaseFoco iniciarFoco(Long assunto) {
        return foco.iniciar(UUID.randomUUID(), Especie.FOCO, assunto, null);
    }

    private void passar(Duration d) {
        RELOGIO.avancar(d);
    }

    private SessaoRequest estudo(long assunto, int minutos, List<Long> fases) {
        return new SessaoRequest(assunto, TipoSessao.ESTUDO, LocalDate.of(2026, 10, 30), minutos, null, null, null, null, null, null,
            UUID.randomUUID(), null, fases);
    }

    // ---------- D-98 ----------

    @Test
    void d98_segundaFaseEmCurso_ehRecusada() {
        iniciarFoco(null);
        var e = assertThrows(BusinessRuleException.class, () -> iniciarFoco(null));
        assertEquals("FASE_EM_CURSO", e.getCodigo());
    }

    @Test
    void d98_aUnicidadeMoraNoBanco_naoNoServico() {
        iniciarFoco(null);
        var e = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
            "INSERT INTO fase_foco (tentativa_id, especie, inicio, duracao_programada_s) VALUES (?, 'FOCO', now(), 60)", UUID.randomUUID()));
        assertTrue(e.getMostSpecificCause().getMessage().contains("ux_fase_foco_d98_uma_em_curso"));
    }

    @Test
    void d98_mesmaTentativa_devolveAMesmaFase() {
        var tentativa = UUID.randomUUID();
        var a = foco.iniciar(tentativa, Especie.FOCO, null, null);
        var b = foco.iniciar(tentativa, Especie.FOCO, null, null);
        assertEquals(a.id(), b.id());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM fase_foco", Integer.class));
    }

    @Test
    void d98_iniciarComFaseVencida_encerraAVencidaEAbreANova() {
        var primeira = iniciarFoco(null);
        passar(Duration.ofMinutes(26));
        var pausa = foco.iniciar(UUID.randomUUID(), Especie.PAUSA_CURTA, null, null);
        assertEquals("VENCIDA", jdbc.queryForObject("SELECT motivo_fim FROM fase_foco WHERE id = ?", String.class, primeira.id()));
        assertEquals(T0.plusSeconds(25 * 60),
            jdbc.queryForObject("SELECT fim FROM fase_foco WHERE id = ?", java.time.OffsetDateTime.class, primeira.id()).toInstant());
        assertEquals(5 * 60, pausa.duracaoProgramadaS());
    }

    // ---------- D-99 ----------

    @Test
    void d99_focoVencidoQueNinguemEncerrou_contaSoADuracao_mesmoVistoDuasHorasDepois() {
        var assunto = novoAssunto();
        iniciarFoco(assunto);
        passar(Duration.ofHours(2));
        assertTrue(foco.atual().isEmpty(), "a fase vencida foi encerrada pela consulta");
        assertEquals(25, foco.livres(assunto).minutos());
    }

    @Test
    void d99_pausaManualCongelaOTempoEOVencimentoAndaJunto() {
        var f = iniciarFoco(null);
        passar(Duration.ofMinutes(10));
        foco.pausar();
        passar(Duration.ofMinutes(30));
        var retomada = foco.retomar();
        assertEquals(10 * 60, retomada.decorridoS());
        assertEquals(15 * 60, retomada.restanteS());
        passar(Duration.ofMinutes(14));
        assertTrue(foco.atual().isPresent());
        passar(Duration.ofMinutes(1));
        assertTrue(foco.atual().isEmpty());
        assertEquals(Motivo.VENCIDA.name(), jdbc.queryForObject("SELECT motivo_fim FROM fase_foco WHERE id = ?", String.class, f.id()));
    }

    @Test
    void d99_encerrarAntesDoFim_contaOQueCorreu() {
        var assunto = novoAssunto();
        iniciarFoco(assunto);
        passar(Duration.ofMinutes(12));
        var encerrada = foco.encerrar();
        assertEquals(Motivo.ENCERRADA, encerrada.motivoFim());
        assertEquals(12, foco.livres(assunto).minutos());
    }

    @Test
    void d99_pausaCurtaNaoEntraNaSoma() {
        var assunto = novoAssunto();
        iniciarFoco(assunto);
        passar(Duration.ofMinutes(25));
        foco.iniciar(UUID.randomUUID(), Especie.PAUSA_CURTA, null, null);
        passar(Duration.ofMinutes(5));
        foco.atual();
        assertEquals(25, foco.livres(assunto).minutos());
    }

    // ---------- D-100, D-101 ----------

    @Test
    void d100_registrarComFocosLivres_trazASomaECorrigirGravaOCorrigido() throws Exception {
        var assunto = novoAssunto();
        iniciarFoco(assunto);
        passar(Duration.ofMinutes(25));
        foco.atual();
        iniciarFoco(assunto);
        passar(Duration.ofMinutes(20));
        foco.encerrar();

        mockMvc.perform(get("/api/foco/livres").param("assuntoId", String.valueOf(assunto)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.minutos").value(45)).andExpect(jsonPath("$.fasesIds.length()").value(2));

        var livres = foco.livres(assunto);
        var sessao = sessoes.registrar(estudo(assunto, 40, livres.fasesIds()));   // a pessoa corrigiu 45 para 40
        assertEquals(40, sessao.getTempoMinutos());
    }

    @Test
    void d101_focoUsadoPorUmaSessao_naoServeAOutra_eASessaoNaoGrava() {
        var assunto = novoAssunto();
        iniciarFoco(assunto);
        passar(Duration.ofMinutes(25));
        foco.atual();
        var ids = foco.livres(assunto).fasesIds();
        sessoes.registrar(estudo(assunto, 25, ids));

        assertTrue(foco.livres(assunto).fasesIds().isEmpty());
        var nova = estudo(assunto, 25, ids);
        assertThrows(ValidationException.class, () -> sessoes.registrar(nova));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sessao WHERE tentativa_id = ?", Integer.class, nova.tentativaId()),
            "foco inválido desfaz a sessão inteira");
    }

    @Test
    void d101_focoDeOutroAssuntoOuEmCurso_ehRecusado() {
        var a = novoAssunto();
        var b = novoAssunto();
        var emCurso = iniciarFoco(a);
        assertThrows(ValidationException.class, () -> sessoes.registrar(estudo(a, 10, List.of(emCurso.id()))));
        passar(Duration.ofMinutes(25));
        foco.atual();
        assertThrows(ValidationException.class, () -> sessoes.registrar(estudo(b, 10, List.of(emCurso.id()))));
    }

    @Test
    void d101_recuperacao_consomeOFocoSemMudarOTempoDaTela() {
        var assunto = novoAssunto();
        iniciarFoco(assunto);
        passar(Duration.ofMinutes(25));
        foco.atual();
        var ids = foco.livres(assunto).fasesIds();
        var rec = new SessaoRequest(assunto, TipoSessao.RECUPERACAO, LocalDate.of(2026, 10, 30), 8, null, null, null, null,
            br.com.estudos.shared.enums.ResultadoSessao.SUCESSO, br.com.estudos.shared.enums.ResultadoSessao.SUCESSO, UUID.randomUUID(), null, ids);
        var sessao = sessoes.registrar(rec);
        assertEquals(8, sessao.getTempoMinutos(), "o tempo da recuperação continua o da tela");
        assertTrue(foco.livres(assunto).fasesIds().isEmpty(), "o foco não pré-preenche depois uma sessão de conteúdo");
    }

    @Test
    void d101_sessaoNaPausa_ehRecusadaPeloBanco() {
        var assunto = novoAssunto();
        var sessao = sessoes.registrar(estudo(assunto, 5, null));
        foco.iniciar(UUID.randomUUID(), Especie.PAUSA_CURTA, null, null);
        var e = assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update("UPDATE fase_foco SET sessao_id = ? WHERE especie = 'PAUSA_CURTA'", sessao.getId()));
        assertTrue(e.getMostSpecificCause().getMessage().contains("ck_fase_foco_d101_sessao_so_em_foco"));
    }

    // ---------- D-102 ----------

    @Test
    void d102_proposta_soDerivaENuncaCriaFase() {
        var assunto = novoAssunto();
        iniciarFoco(assunto);
        passar(Duration.ofMinutes(25));
        int antes = jdbc.queryForObject("SELECT count(*) FROM fase_foco", Integer.class);
        var proposta = foco.proposta().orElseThrow();
        assertEquals(Especie.PAUSA_CURTA, proposta.especie());
        assertEquals(antes, jdbc.queryForObject("SELECT count(*) FROM fase_foco", Integer.class));
        assertTrue(foco.atual().isEmpty(), "terminar a fase não cria a seguinte");
    }

    @Test
    void d102_quartoFocoCompleto_propoeAPausaLonga_eDepoisDaPausaPropoeFocoNoMesmoAssunto() {
        var assunto = novoAssunto();
        for (int i = 0; i < 4; i++) {
            iniciarFoco(assunto);
            passar(Duration.ofMinutes(25));
            foco.atual();
            if (i < 3) {
                assertEquals(Especie.PAUSA_CURTA, foco.proposta().orElseThrow().especie());
                foco.iniciar(UUID.randomUUID(), Especie.PAUSA_CURTA, null, null);
                passar(Duration.ofMinutes(5));
                foco.atual();
            }
        }
        var proposta = foco.proposta().orElseThrow();
        assertEquals(Especie.PAUSA_LONGA, proposta.especie());
        assertEquals(15 * 60, proposta.duracaoS());

        foco.iniciar(UUID.randomUUID(), Especie.PAUSA_LONGA, null, null);
        passar(Duration.ofMinutes(15));
        var depois = foco.proposta().orElseThrow();
        assertEquals(Especie.FOCO, depois.especie());
        assertEquals(assunto, depois.assuntoId());
        assertEquals(0, depois.focosSeguidos(), "a pausa longa zera a contagem");
    }

    // ---------- D-103 ----------

    @Test
    void d103_mudarOParametro_naoAlteraAFaseEmCurso_masValeParaAProxima() {
        var f = iniciarFoco(null);
        jdbc.update("UPDATE parametro SET valor = '50' WHERE chave = 'pomodoro_foco_min'");
        passar(Duration.ofMinutes(26));
        assertTrue(foco.atual().isEmpty(), "a fase em curso seguiu a duração de quando começou");
        assertEquals(50 * 60, iniciarFoco(null).duracaoProgramadaS());
        assertEquals(25 * 60, jdbc.queryForObject("SELECT duracao_programada_s FROM fase_foco WHERE id = ?", Integer.class, f.id()));
    }

    // ---------- D-105 ----------

    @Test
    void d105_resumo_porDiaECompletosContraEncerradosAntes() throws Exception {
        var assunto = novoAssunto();
        iniciarFoco(assunto);
        passar(Duration.ofMinutes(25));
        foco.atual();
        iniciarFoco(assunto);
        passar(Duration.ofMinutes(10));
        foco.encerrar();
        var dia = LocalDate.now(RELOGIO);
        var r = foco.resumo(dia, dia);
        assertEquals(1, r.completos());
        assertEquals(1, r.encerradosAntes());
        assertEquals(35 * 60, r.porDia().get(0).segundos());
        mockMvc.perform(get("/api/foco/resumo").param("de", dia.toString()).param("ate", dia.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.completos").value(1));
    }

    @Test
    void d105_estimadoXReal_porSegmento() {
        var assunto = novoAssunto();
        long segmento = jdbc.queryForObject("""
            INSERT INTO segmento (assunto_id, chave_externa, ordem, arquivo, tempo_estimado_min) VALUES (?, ?, 1, 'x.pdf', 60) RETURNING id""",
            Long.class, assunto, "seg-foco-" + System.nanoTime());
        foco.iniciar(UUID.randomUUID(), Especie.FOCO, assunto, segmento);
        passar(Duration.ofMinutes(25));
        foco.atual();
        var ids = foco.livres(assunto).fasesIds();
        sessoes.registrar(new SessaoRequest(assunto, TipoSessao.ESTUDO, LocalDate.of(2026, 10, 30), 25, null, null, null, null, null, null,
            UUID.randomUUID(), segmento, ids));
        var linha = foco.porSegmento(assunto).get(0);
        assertEquals(60, linha.estimadoMin());
        assertEquals(25, linha.realMin());
    }

    @Test
    void d105_naoExisteColunaQueGuardeADerivacao() {
        var colunas = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'fase_foco'", String.class);
        assertFalse(colunas.stream().anyMatch(c -> c.contains("corrido") || c.contains("completo") || c.contains("total") || c.contains("tempo")),
            "tempo corrido, completo e total do dia são consultas (D-16, D-105): " + colunas);
    }

    // ---------- rotas ----------

    @Test
    void rotas_iniciarAtualEncerrar() throws Exception {
        var tentativa = UUID.randomUUID();
        mockMvc.perform(get("/api/foco/atual")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/foco/iniciar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tentativaId\":\"%s\",\"especie\":\"FOCO\"}".formatted(tentativa)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.especie").value("FOCO"));
        mockMvc.perform(post("/api/foco/iniciar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"tentativaId\":\"%s\",\"especie\":\"FOCO\"}".formatted(UUID.randomUUID())))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("FASE_EM_CURSO"));
        mockMvc.perform(get("/api/foco/atual")).andExpect(status().isOk()).andExpect(jsonPath("$.restanteS").value(25 * 60));
        mockMvc.perform(post("/api/foco/atual/encerrar")).andExpect(status().isOk()).andExpect(jsonPath("$.motivoFim").value("ENCERRADA"));
        mockMvc.perform(post("/api/foco/atual/encerrar")).andExpect(status().isNotFound());
    }
}
