package br.com.estudos.reinicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** Sprint 43 (01_DOMINIO §3.27, D-163 a D-165): reiniciar um concurso ou tudo, a confirmação e desfazer. */
@Transactional
class ReinicioTest extends IntegracaoTestBase {

    private static final LocalDate D0 = LocalDate.of(2026, 4, 6);

    @Autowired private DisciplinaService disciplinaService;
    @Autowired private AssuntoService assuntoService;
    @Autowired private JdbcTemplate jdbc;

    private long disciplina;

    private long assunto(String nome) {
        if (disciplina == 0) disciplina = disciplinaService.criar(new DisciplinaRequest("Disc Reinício " + UUID.randomUUID(), TipoPeso.MEDIO)).getId();
        return assuntoService.criar(new AssuntoRequest(disciplina, nome, TipoPeso.BAIXO, (short) 3,
            jdbc.queryForObject("SELECT count(*) + 1 FROM assunto WHERE disciplina_id = ?", Integer.class, disciplina), null)).getId();
    }

    private long concurso(String nome, long... assuntos) {
        var c = jdbc.queryForObject("INSERT INTO concurso (nome, origem, slug, ativo) VALUES (?, 'PLANEJAMENTO', ?, false) RETURNING id",
            Long.class, nome, "r-" + UUID.randomUUID());
        int ordem = 1;
        for (var a : assuntos) {
            jdbc.update("INSERT INTO concurso_assunto (concurso_id, assunto_id, disciplina_id, peso, ordem) VALUES (?, ?, ?, 'MEDIO', ?)", c, a, disciplina, ordem++);
        }
        return c;
    }

    private void estudo(long assuntoId, LocalDate data) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 20, "tentativaId": "%s"}""".formatted(assuntoId, data, UUID.randomUUID())))
            .andExpect(status().isCreated());
    }

    private int ativas(long assuntoId) {
        return jdbc.queryForObject("SELECT count(*) FROM sessao WHERE assunto_id = ?", Integer.class, assuntoId);
    }

    private int revisoes(long assuntoId) {
        return jdbc.queryForObject("SELECT count(*) FROM revisao WHERE assunto_id = ?", Integer.class, assuntoId);
    }

    private org.springframework.test.web.servlet.ResultActions reiniciar(Long concursoId, String palavra) throws Exception {
        return mockMvc.perform(post("/api/reinicios").contentType(MediaType.APPLICATION_JSON)
            .content("{\"concursoId\": " + concursoId + ", \"confirmacao\": \"" + palavra + "\"}"));
    }

    @Test
    void concurso_zeraACoberturaComOsDivididos_eDesfazerDevolve() throws Exception {
        var crase = assunto("Crase R");
        var regencia = assunto("Regência R");
        var fora = assunto("Fora R");
        var tce = concurso("TCE R", crase, regencia);
        concurso("TJ R", regencia);
        estudo(crase, D0);
        estudo(crase, D0.plusDays(1));
        estudo(regencia, D0);
        estudo(fora, D0);

        mockMvc.perform(get("/api/reinicios/previa").param("concursoId", Long.toString(tce)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.escopo").value("TCE R")).andExpect(jsonPath("$.sessoes").value(3)).andExpect(jsonPath("$.assuntos").value(2))
            .andExpect(jsonPath("$.divididos[0].assunto").value("Regência R")).andExpect(jsonPath("$.divididos[0].outros[0]").value("TJ R"));

        var r = reiniciar(tce, "REINICIAR").andExpect(status().isOk()).andExpect(jsonPath("$.sessoes").value(3)).andReturn();
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
        assertEquals(0, ativas(crase));
        assertEquals(0, ativas(regencia), "o dividido zera também");
        assertEquals(0, revisoes(crase), "volta a nunca visto");
        assertEquals(1, ativas(fora), "fora da cobertura, fica");
        assertEquals(1, revisoes(fora));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM sessao_registro WHERE assunto_id IN (?, ?)", Integer.class, crase, regencia), "nada apagado");

        // uma sessão nova depois do reinício entra junto ao desfazer, pela data
        estudo(crase, D0.plusDays(5));
        mockMvc.perform(post("/api/reinicios/" + id + "/desfazer")).andExpect(status().isOk()).andExpect(jsonPath("$.sessoes").value(3));
        assertEquals(3, ativas(crase));
        assertEquals(1, ativas(regencia));
        assertEquals(1, revisoes(crase));
        assertEquals(java.sql.Date.valueOf(D0), jdbc.queryForObject(
            "SELECT s.data FROM revisao r JOIN sessao s ON s.id = r.sessao_origem_id WHERE r.assunto_id = ? AND r.situacao = 'PENDENTE'",
            java.sql.Date.class, crase), "a escada refeita pela ordem: a pendente vem do estudo mais antigo");

        mockMvc.perform(post("/api/reinicios/" + id + "/desfazer")).andExpect(status().isConflict());
        mockMvc.perform(get("/api/reinicios")).andExpect(jsonPath("$[0].escopo").value("TCE R")).andExpect(jsonPath("$[0].desfeitoEm").isNotEmpty());
    }

    @Test
    void tudo_zeraTodasAsSessoes() throws Exception {
        var a = assunto("Tudo R");
        estudo(a, D0);
        reiniciar(null, "REINICIAR").andExpect(status().isOk()).andExpect(jsonPath("$.sessoes").isNumber());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sessao", Integer.class));
        mockMvc.perform(get("/api/reinicios")).andExpect(jsonPath("$[0].escopo").value("Tudo"));
    }

    @Test
    void semAPalavra_naoReinicia() throws Exception {
        var a = assunto("Palavra R");
        estudo(a, D0);
        reiniciar(null, "reiniciar").andExpect(status().isUnprocessableEntity());
        assertEquals(1, ativas(a));
    }

    @Test
    void semProgresso_recusa() throws Exception {
        var c = concurso("Vazio R", assunto("Vazio R"));
        reiniciar(c, "REINICIAR").andExpect(status().isConflict());
    }
}
