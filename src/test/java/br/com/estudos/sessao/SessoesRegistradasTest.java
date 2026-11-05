package br.com.estudos.sessao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
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
import br.com.estudos.shared.enums.CausaErro;
import br.com.estudos.shared.enums.NivelConfianca;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** Sprint 42 (01_DOMINIO §3.26, D-160 a D-162; ADR-062): tirar sessões da escada, a escada refeita e pôr de volta. */
@Transactional
class SessoesRegistradasTest extends IntegracaoTestBase {

    private static final LocalDate D0 = LocalDate.of(2026, 3, 2);

    @Autowired private DisciplinaService disciplinaService;
    @Autowired private AssuntoService assuntoService;
    @Autowired private JdbcTemplate jdbc;

    private long novoAssunto(String nome) {
        var d = disciplinaService.criar(new DisciplinaRequest("Disciplina " + nome, TipoPeso.MEDIO));
        return assuntoService.criar(new AssuntoRequest(d.getId(), nome, TipoPeso.BAIXO, (short) 3, 1, null)).getId();
    }

    private long registrar(String corpo) throws Exception {
        var r = mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long estudo(long assuntoId, LocalDate data) throws Exception {
        return registrar("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 20, "tentativaId": "%s"}""".formatted(assuntoId, data, UUID.randomUUID()));
    }

    private long recuperacao(long assuntoId, LocalDate data) throws Exception {
        return registrar("""
            {"assuntoId": %d, "tipo": "RECUPERACAO", "data": "%s", "tempoMinutos": 8,
             "previsaoReconstrucao": "SUCESSO", "resultado": "SUCESSO", "tentativaId": "%s"}""".formatted(assuntoId, data, UUID.randomUUID()));
    }

    private long questoes(long assuntoId, LocalDate data, int corretas, int total) throws Exception {
        return registrar("""
            {"assuntoId": %d, "tipo": "QUESTOES", "data": "%s", "tempoMinutos": 10, "questoesCorretas": %d, "questoesTotal": %d,
             "formato": "MULTIPLA_ESCOLHA", "previsaoPercentual": 50, "tentativaId": "%s"}""".formatted(assuntoId, data, corretas, total, UUID.randomUUID()));
    }

    private void gesto(String qual, long... ids) throws Exception {
        var lista = java.util.Arrays.stream(ids).mapToObj(Long::toString).toList();
        mockMvc.perform(post("/api/sessoes/registradas/" + qual).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\": [" + String.join(",", lista) + "]}"))
            .andExpect(status().isOk());
    }

    /** A pendente: {nivel, origem, data}. */
    private Map<String, Object> pendente(long assuntoId) {
        var l = jdbc.queryForList("SELECT nivel, sessao_origem_id AS origem, data_prevista AS data FROM revisao WHERE assunto_id = ? AND situacao = 'PENDENTE'", assuntoId);
        assertTrue(l.size() <= 1);
        return l.isEmpty() ? null : l.get(0);
    }

    private int intervalo(int nivel) {
        return Integer.parseInt(jdbc.queryForObject("SELECT valor FROM parametro WHERE chave = ?", String.class, "intervalo_nivel_" + nivel));
    }

    @Test
    void tirarARecuperacao_voltaAoDegrauDoEstudo_ePorDeVoltaRefazOAvanco() throws Exception {
        var a = novoAssunto("Crase 42");
        var e = estudo(a, D0);
        var dataRec = D0.plusDays(intervalo(1));
        var r = recuperacao(a, dataRec);
        assertEquals(2, ((Number) pendente(a).get("nivel")).intValue());

        mockMvc.perform(post("/api/sessoes/registradas/tirar").contentType(MediaType.APPLICATION_JSON).content("{\"ids\": [" + r + "]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.sessoes").value(1)).andExpect(jsonPath("$.assuntos[0]").value("Crase 42"));
        var p = pendente(a);
        assertEquals(1, ((Number) p.get("nivel")).intValue());
        assertEquals(e, ((Number) p.get("origem")).longValue());
        assertEquals(java.sql.Date.valueOf(dataRec), p.get("data"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM revisao WHERE assunto_id = ?", Integer.class, a), "a escada refeita, sem a cumprida");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM sessao WHERE id = ?", Integer.class, r), "a visão não mostra a tirada");

        gesto("por-de-volta", r);
        p = pendente(a);
        assertEquals(2, ((Number) p.get("nivel")).intValue());
        assertEquals(r, ((Number) p.get("origem")).longValue());
    }

    @Test
    void tirarDoMeio_asSeguintesSaoRecalculadasSemEla() throws Exception {
        var a = novoAssunto("Regência 42");
        estudo(a, D0);
        var d1 = D0.plusDays(intervalo(1));
        var r1 = recuperacao(a, d1);
        var d2 = d1.plusDays(intervalo(2));
        var r2 = recuperacao(a, d2);
        assertEquals(3, ((Number) pendente(a).get("nivel")).intValue());

        gesto("tirar", r1);
        var p = pendente(a);
        assertEquals(2, ((Number) p.get("nivel")).intValue(), "sem r1, r2 cumpre o nível 1 e sobe para o 2");
        assertEquals(r2, ((Number) p.get("origem")).longValue());
        assertEquals(java.sql.Date.valueOf(d2.plusDays(intervalo(2))), p.get("data"));
    }

    @Test
    void tirarAUnicaSessao_oAssuntoVoltaANuncaVisto() throws Exception {
        var a = novoAssunto("Pronomes 42");
        var e = estudo(a, D0);
        gesto("tirar", e);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM revisao WHERE assunto_id = ?", Integer.class, a));
        mockMvc.perform(get("/api/sessoes").param("assuntoId", Long.toString(a))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/sessoes/registradas").param("ate", D0.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessoes[0].id").value(e)).andExpect(jsonPath("$.sessoes[0].ativa").value(false));
    }

    @Test
    void aPendenteQueEAMesma_guardaADataQueTinha() throws Exception {
        var a = novoAssunto("Vírgula 42");
        estudo(a, D0);
        var q = questoes(a, D0.plusDays(1), 1, 2); // abaixo do lote mínimo: não move a escada (D-09)
        var antecipada = D0.plusDays(30);
        jdbc.update("UPDATE revisao SET data_prevista = ?, data_prevista_antes_reta_final = ? WHERE assunto_id = ? AND situacao = 'PENDENTE'",
            antecipada, D0.plusDays(intervalo(1)), a);
        gesto("tirar", q);
        assertEquals(java.sql.Date.valueOf(antecipada), pendente(a).get("data"));
        assertEquals(java.sql.Date.valueOf(D0.plusDays(intervalo(1))),
            jdbc.queryForObject("SELECT data_prevista_antes_reta_final FROM revisao WHERE assunto_id = ? AND situacao = 'PENDENTE'", java.sql.Date.class, a));
    }

    @Test
    void oErroDaSessaoTiradaSaiJunto() throws Exception {
        var a = novoAssunto("Concordância 42");
        estudo(a, D0);
        var q = questoes(a, D0.plusDays(1), 6, 10);
        jdbc.update("INSERT INTO erro (assunto_id, sessao_id, descricao, causa, confianca) VALUES (?, ?, 'x', ?, ?)",
            a, q, CausaErro.values()[0].name(), NivelConfianca.values()[0].name());
        jdbc.update("INSERT INTO erro (assunto_id, descricao, causa, confianca) VALUES (?, 'solto', ?, ?)",
            a, CausaErro.values()[0].name(), NivelConfianca.values()[0].name());
        mockMvc.perform(get("/api/erros").param("assuntoId", Long.toString(a))).andExpect(jsonPath("$.length()").value(2));
        gesto("tirar", q);
        mockMvc.perform(get("/api/erros").param("assuntoId", Long.toString(a)))
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].descricao").value("solto"));
    }

    @Test
    void semIds_recusa() throws Exception {
        mockMvc.perform(post("/api/sessoes/registradas/tirar").contentType(MediaType.APPLICATION_JSON).content("{\"ids\": []}"))
            .andExpect(status().isUnprocessableEntity());
    }
}
