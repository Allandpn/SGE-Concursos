package br.com.estudos.frente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** Cartão de disciplina em Assuntos (02_JORNADAS §4.5): contagem das fases derivadas. */
class DisciplinaResumoTest extends IntegracaoTestBase {

    @Autowired private DisciplinaService disciplinaService;
    @Autowired private AssuntoService assuntoService;

    @Test
    void contaBacklogEFrentePorDisciplina() throws Exception {
        var nome = "Resumo Disciplina " + System.nanoTime();
        var disciplina = disciplinaService.criar(new DisciplinaRequest(nome, TipoPeso.ALTO));
        var estudado = assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Estudado", TipoPeso.ALTO, (short) 3, 1, null));
        assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Nao iniciado", TipoPeso.ALTO, (short) 3, 2, null));
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
                {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 30, "tentativaId": "%s"}
                """.formatted(estudado.getId(), LocalDate.now(), UUID.randomUUID())))
            .andExpect(status().isCreated());

        var corpo = mockMvc.perform(get("/api/disciplinas/resumo")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> linhas = JsonPath.read(corpo, "$[?(@.nome == '" + nome + "')]");
        assertEquals(1, linhas.size());
        var linha = linhas.get(0);
        assertEquals("ALTO", linha.get("peso"));
        assertEquals(1, ((Number) linha.get("backlog")).intValue());
        assertEquals(1, ((Number) linha.get("frente")).intValue());
        assertEquals(0, ((Number) linha.get("consolidados")).intValue());
        assertEquals(2, ((Number) linha.get("total")).intValue());
    }
}
