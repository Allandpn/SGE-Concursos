package br.com.estudos.qconcursos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** D-156 (docs/SPRINT-36-QCONCURSOS.md §4): os erros anotados depois do lote, com o número Q. */
@Transactional
class ErroQConcursosTest extends IntegracaoTestBase {

    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;

    String erro(long assunto, long sessao, String questao) {
        return """
            {"assuntoId": %d, "sessaoId": %d, "descricao": "Assistir no sentido de ver pede a", "causa": "ESQUECIMENTO",
             "confianca": "ALTA", "questaoExterna": %s}""".formatted(assunto, sessao, questao == null ? "null" : "\"" + questao + "\"");
    }

    @Test
    void d156_numeroNormalizado_formatoInvalido_eLimiteDoLote() throws Exception {
        var disciplina = disciplinas.criar(new DisciplinaRequest("Erros QC " + System.nanoTime(), TipoPeso.MEDIO)).getId();
        var assunto = assuntos.criar(new AssuntoRequest(disciplina, "Regência", TipoPeso.MEDIO, (short) 3, 1, null)).getId();
        var corpo = mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
                {"assuntoId": %d, "tipo": "QUESTOES", "data": "%s", "tempoMinutos": 24, "questoesCorretas": 6, "questoesTotal": 8,
                 "formato": "MULTIPLA_ESCOLHA", "previsaoPercentual": 70, "tentativaId": "%s"}"""
                .formatted(assunto, LocalDate.now(), UUID.randomUUID())))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long sessao = ((Number) JsonPath.read(corpo, "$.id")).longValue();

        mockMvc.perform(post("/api/erros").contentType(MediaType.APPLICATION_JSON).content(erro(assunto, sessao, "ABC")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("QUESTAO_INVALIDA"));
        mockMvc.perform(post("/api/erros").contentType(MediaType.APPLICATION_JSON).content(erro(assunto, sessao, " q2318890 ")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.questaoExterna").value("Q2318890"))
            .andExpect(jsonPath("$.sessaoId").value(sessao));
        mockMvc.perform(post("/api/erros").contentType(MediaType.APPLICATION_JSON).content(erro(assunto, sessao, null)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.questaoExterna").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(post("/api/erros").contentType(MediaType.APPLICATION_JSON).content(erro(assunto, sessao, "Q1234567")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ERROS_ACIMA_DO_LOTE"));
    }
}
