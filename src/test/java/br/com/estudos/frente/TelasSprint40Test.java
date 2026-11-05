package br.com.estudos.frente;

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

/** Sprint 40: o painel de Assuntos numa consulta só e a lista geral de erros, com os filtros e o resolvido. */
@Transactional
class TelasSprint40Test extends IntegracaoTestBase {

    @Autowired private DisciplinaService disciplinaService;
    @Autowired private AssuntoService assuntoService;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void painel_trazFaseERevisaoPorDisciplina() throws Exception {
        var nome = "Painel " + UUID.randomUUID();
        var d = disciplinaService.criar(new DisciplinaRequest(nome, TipoPeso.ALTO));
        var lido = assuntoService.criar(new AssuntoRequest(d.getId(), "Lido", TipoPeso.BAIXO, (short) 3, 1, null));
        assuntoService.criar(new AssuntoRequest(d.getId(), "Na fila", TipoPeso.BAIXO, (short) 3, 2, null));
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 20, "tentativaId": "%s"}""".formatted(lido.getId(), LocalDate.of(2026, 5, 4), UUID.randomUUID())))
            .andExpect(status().isCreated());
        var g = "$[?(@.disciplina.nome == '" + nome + "')]";
        mockMvc.perform(get("/api/assuntos/painel")).andExpect(status().isOk())
            .andExpect(jsonPath(g + ".assuntos[0].assunto.nome").value("Lido"))
            .andExpect(jsonPath(g + ".assuntos[0].fase").value("FRENTE"))
            .andExpect(jsonPath(g + ".assuntos[0].proximaRevisao").value("2026-05-05"))
            .andExpect(jsonPath(g + ".assuntos[1].fase").value("BACKLOG"));
    }

    @Test
    void errosGerais_filtrosEResolvido() throws Exception {
        var d = disciplinaService.criar(new DisciplinaRequest("Erros " + UUID.randomUUID(), TipoPeso.ALTO));
        var a = assuntoService.criar(new AssuntoRequest(d.getId(), "Crase E", TipoPeso.BAIXO, (short) 3, 1, null));
        var e1 = jdbc.queryForObject("INSERT INTO erro (assunto_id, descricao, causa, confianca) VALUES (?, 'um', 'DESATENCAO', 'ALTA') RETURNING id", Long.class, a.getId());
        jdbc.update("INSERT INTO erro (assunto_id, descricao, causa, confianca) VALUES (?, 'dois', 'FALTA_CONHECIMENTO', 'MEDIA')", a.getId());
        var did = Long.toString(d.getId());
        mockMvc.perform(get("/api/erros/todos").param("disciplinaId", did)).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].assunto").value("Crase E"));
        mockMvc.perform(get("/api/erros/todos").param("disciplinaId", did).param("causa", "DESATENCAO"))
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].descricao").value("um"));
        mockMvc.perform(post("/api/erros/" + e1 + "/resolvido").contentType(MediaType.APPLICATION_JSON).content("{\"resolvido\": true}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/erros/todos").param("disciplinaId", did).param("naoResolvidos", "true"))
            .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].descricao").value("dois"));
        mockMvc.perform(post("/api/erros/999999999/resolvido").contentType(MediaType.APPLICATION_JSON).content("{\"resolvido\": true}"))
            .andExpect(status().isNotFound());
    }
}
