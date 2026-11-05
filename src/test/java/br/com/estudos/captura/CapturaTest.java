package br.com.estudos.captura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** Sprint 37 (01_DOMINIO §3.28, D-166 a D-171; ADR-063): a chave, o envio, a caixa, o par, o registro, o lote e o diagnóstico. */
@Transactional
class CapturaTest extends IntegracaoTestBase {

    @Autowired private DisciplinaService disciplinaService;
    @Autowired private AssuntoService assuntoService;
    @Autowired private JdbcTemplate jdbc;

    private String chave() throws Exception {
        var r = mockMvc.perform(post("/api/captura/chaves").contentType(MediaType.APPLICATION_JSON).content("{\"nome\": \"Firefox no celular\"}"))
            .andExpect(status().isOk()).andReturn();
        return com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.chave");
    }

    private ResultActions enviar(String chave, String corpo) throws Exception {
        var req = post("/captura/qconcursos/respostas").contentType(MediaType.APPLICATION_JSON).content(corpo);
        if (chave != null) req = req.header("Authorization", "Bearer " + chave);
        return mockMvc.perform(req);
    }

    private static String questao(String codigo, boolean acertou, String assuntoQc, String gabarito) {
        return """
            {"codigo": "%s", "acertou": %s, "resolvidaEm": "2026-06-01T15:00:00Z", "disciplina": "Português", "assuntos": ["%s"],
             "banca": "FGV", "ano": 2024, "gabarito": "%s", "endereco": "https://www.qconcursos.com/questoes-de-concursos/questoes"}"""
            .formatted(codigo, acertou, assuntoQc, gabarito);
    }

    private long assunto(String nome) {
        var d = disciplinaService.criar(new DisciplinaRequest("Cap " + UUID.randomUUID(), TipoPeso.ALTO));
        return assuntoService.criar(new AssuntoRequest(d.getId(), nome, TipoPeso.BAIXO, (short) 3, 1, null)).getId();
    }

    @Test
    void aPorta_recusaSemChaveComChaveErradaRevogadaEForaDoFormato() throws Exception {
        enviar(null, questao("Q100001", true, "Crase", "A")).andExpect(status().isUnauthorized());
        enviar("eag_errada", questao("Q100001", true, "Crase", "A")).andExpect(status().isUnauthorized());
        var k = chave();
        mockMvc.perform(post("/captura/qconcursos/respostas").header("Authorization", "Bearer " + k).contentType(MediaType.TEXT_PLAIN).content("x"))
            .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/captura/qconcursos/respostas").header("Authorization", "Bearer " + k).contentType(MediaType.APPLICATION_JSON)
            .content("{\"codigo\": \"" + "x".repeat(70_000) + "\"}")).andExpect(status().isPayloadTooLarge());
        mockMvc.perform(get("/captura/qconcursos/respostas").header("Authorization", "Bearer " + k)).andExpect(status().isMethodNotAllowed());
        enviar(k, questao("Q100001", true, "Crase", "A")).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        var id = jdbc.queryForObject("SELECT id FROM captura_chave ORDER BY id DESC LIMIT 1", Long.class);
        mockMvc.perform(get("/api/captura/chaves")).andExpect(jsonPath("$[0].ultimoEnvioEm").isNotEmpty());
        mockMvc.perform(post("/api/captura/chaves/" + id + "/revogar")).andExpect(status().isNoContent());
        enviar(k, questao("Q100002", true, "Crase", "A")).andExpect(status().isUnauthorized());
        assertEquals(64, jdbc.queryForObject("SELECT length(resumo) FROM captura_chave WHERE id = ?", Integer.class, id), "só o resumo fica");
    }

    @Test
    void semAssunto_naCaixa_pareiaRegistraComPrevisaoEAprende() throws Exception {
        var k = chave();
        var crase = assunto("Crase Cap");
        var nomeQc = "Crase " + UUID.randomUUID();
        enviar(k, questao("Q200001", true, nomeQc, "A")).andExpect(jsonPath("$.assunto").doesNotExist());
        enviar(k, questao("Q200002", false, nomeQc, "C"));
        enviar(k, questao("Q200002", false, nomeQc, "C")).andExpect(jsonPath("$.repetida").value(true));
        mockMvc.perform(get("/api/captura/caixa"))
            .andExpect(jsonPath("$[?(@.nomeQc == '" + nomeQc + "')].questoes").value(2));

        mockMvc.perform(post("/api/captura/pares").contentType(MediaType.APPLICATION_JSON)
            .content("{\"nomeQc\": \"" + nomeQc + "\", \"assuntoId\": " + crase + "}")).andExpect(jsonPath("$.movidas").value(2));
        enviar(k, questao("Q200003", true, nomeQc.toUpperCase(), "B")).andExpect(jsonPath("$.assunto").value("Crase Cap"));
        mockMvc.perform(get("/api/captura/caixa")).andExpect(jsonPath("$[?(@.assuntoId == " + crase + ")].questoes").value(3));

        mockMvc.perform(post("/api/captura/caixa/registrar").contentType(MediaType.APPLICATION_JSON)
            .content("{\"assuntoId\": " + crase + "}")).andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/captura/caixa/registrar").contentType(MediaType.APPLICATION_JSON)
            .content("{\"assuntoId\": " + crase + ", \"previsaoPercentual\": 80}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.feitas").value(3)).andExpect(jsonPath("$.certas").value(2));
        var s = jdbc.queryForMap("SELECT questoes_total, questoes_corretas, previsao_percentual, data, formato FROM sessao WHERE assunto_id = ?", crase);
        assertEquals(3, s.get("questoes_total"));
        assertEquals(2, s.get("questoes_corretas"));
        assertEquals("MULTIPLA_ESCOLHA", s.get("formato"));
        assertEquals(java.sql.Date.valueOf("2026-06-01"), s.get("data"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM questao_capturada WHERE assunto_id = ? AND situacao = 'NA_CAIXA'", Integer.class, crase));
    }

    @Test
    void oLote_desdeEMarcar_eDescartar() throws Exception {
        var k = chave();
        var a = assunto("Regência Cap");
        var nomeQc = "Regência " + UUID.randomUUID();
        mockMvc.perform(post("/api/captura/pares").contentType(MediaType.APPLICATION_JSON).content("{\"nomeQc\": \"" + nomeQc + "\", \"assuntoId\": " + a + "}"));
        enviar(k, questao("Q300001", true, nomeQc, "Certo"));
        enviar(k, questao("Q300002", false, nomeQc, "Errado"));
        var r = mockMvc.perform(get("/api/captura/desde").param("assuntoId", Long.toString(a)).param("desde", "2026-06-01T14:00:00Z"))
            .andExpect(jsonPath("$.feitas").value(2)).andExpect(jsonPath("$.certas").value(1)).andExpect(jsonPath("$.formato").value("CERTO_ERRADO"))
            .andReturn();
        mockMvc.perform(get("/api/captura/desde").param("assuntoId", Long.toString(a)).param("desde", "2026-06-01T16:00:00Z"))
            .andExpect(jsonPath("$.feitas").value(0));
        java.util.List<Integer> ids = com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.ids");
        var sessao = mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "QUESTOES", "data": "2026-06-01", "tempoMinutos": 6, "questoesCorretas": 1, "questoesTotal": 2,
             "formato": "CERTO_ERRADO", "previsaoPercentual": 60, "tentativaId": "%s"}""".formatted(a, UUID.randomUUID()))).andReturn();
        long sid = ((Number) com.jayway.jsonpath.JsonPath.read(sessao.getResponse().getContentAsString(), "$.id")).longValue();
        mockMvc.perform(post("/api/captura/marcar").contentType(MediaType.APPLICATION_JSON).content("{\"ids\": " + ids + ", \"sessaoId\": " + sid + "}"))
            .andExpect(jsonPath("$.marcadas").value(2));
        enviar(k, questao("Q300003", true, nomeQc, "A"));
        mockMvc.perform(post("/api/captura/caixa/descartar").contentType(MediaType.APPLICATION_JSON).content("{\"assuntoId\": " + a + "}"))
            .andExpect(jsonPath("$.descartadas").value(1));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM questao_capturada WHERE codigo = 'Q300003' AND situacao = 'DESCARTADA'", Integer.class), "nada apagado");
    }

    @Test
    void oDiagnostico_eOUserscriptComOEndereco() throws Exception {
        var k = chave();
        mockMvc.perform(post("/captura/qconcursos/diagnosticos").header("Authorization", "Bearer " + k).contentType(MediaType.APPLICATION_JSON)
            .content("{\"motivo\": \"não li o acerto\", \"endereco\": \"https://www.qconcursos.com/x\", \"recorte\": \"<div>Q123456 …</div>\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/captura/diagnosticos")).andExpect(jsonPath("$[0].motivo").value("não li o acerto"))
            .andExpect(jsonPath("$[0].chave").value("Firefox no celular"));
        var r = mockMvc.perform(get("/captura/eagora-qconcursos.user.js")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(r.contains("const SGE = 'http://localhost'") && r.contains("@connect      localhost") && !r.contains("__SGE__"), r.substring(0, 400));
    }

    @Test
    void pelaArvore_oNoLigadoAUmSoAssunto_porNomeOuNumero() throws Exception {
        var k = chave();
        var a = assunto("Regência Verbal Cap");
        long no = 900_000_000L + (System.nanoTime() % 1_000_000);
        var nome = "Regência Verbal Árvore " + no;
        jdbc.update("UPDATE arvore_versao SET vigente = false WHERE vigente"); // uma só vigente (D-90); a transação do teste desfaz
        var versao = jdbc.queryForObject("""
            INSERT INTO arvore_versao (rotulo, fonte, data, quantidade_nos, vigente) VALUES (?, 'qconcursos', '2026-10-19', 2, true) RETURNING id""",
            Long.class, "cap-" + no);
        jdbc.update("INSERT INTO no_arvore (versao_id, no_id, nome, pai_id, ordem, raiz_id) VALUES (?, -26, 'Português', NULL, 1, -26)", versao);
        jdbc.update("INSERT INTO no_arvore (versao_id, no_id, nome, pai_id, ordem, raiz_id) VALUES (?, ?, ?, -26, 1, -26)", versao, no, nome);
        var concurso = jdbc.queryForObject("INSERT INTO concurso (nome, origem, slug, ativo) VALUES ('C', 'PLANEJAMENTO', ?, false) RETURNING id", Long.class, "c-" + no);
        var analise = jdbc.queryForObject("INSERT INTO analise_edital (nome_arquivo, texto, status, concurso_id) VALUES ('e.pdf', '', 'CONFIRMADA', ?) RETURNING id",
            Long.class, concurso);
        var disc = jdbc.queryForObject("INSERT INTO edital_disciplina (analise_id, ordem, nome) VALUES (?, 1, 'Português') RETURNING id", Long.class, analise);
        var ea = jdbc.queryForObject("INSERT INTO edital_assunto (disciplina_id, chave, ordem, texto, assunto_id) VALUES (?, '1', 1, 't', ?) RETURNING id",
            Long.class, disc, a);
        var parte = jdbc.queryForObject("""
            INSERT INTO edital_parte (assunto_edital_id, chave, ordem, texto, origem, estado, arvore_versao_id)
            VALUES (?, 'a', 1, 'parte', 'IA', 'ACEITA', ?) RETURNING id""", Long.class, ea, versao);
        jdbc.update("INSERT INTO edital_parte_no (parte_id, proposto, ordem, no_id) VALUES (?, false, 1, ?)", parte, no);

        enviar(k, questao("Q400001", true, "regencia verbal arvore " + no, "A")).andExpect(jsonPath("$.assunto").value("Regência Verbal Cap"));
        enviar(k, """
            {"codigo": "Q400002", "acertou": false, "assuntos": ["Outro nome"], "nos": [%d], "gabarito": "B"}""".formatted(no))
            .andExpect(jsonPath("$.assunto").value("Regência Verbal Cap"));
    }
}
