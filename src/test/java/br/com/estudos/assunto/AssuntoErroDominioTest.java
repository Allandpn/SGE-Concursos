package br.com.estudos.assunto;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Um teste por erro de domínio da tabela de docs/SPRINT-2-CADASTRO.md §3.1,
 * conferindo status HTTP e codigo do ProblemDetail (ADR-026) — item 2.8 de
 * PROGRESSO.md. @Transactional aqui: só confere HTTP/JSON, não precisa ver
 * commit real, então ganha limpeza automática de graça (IntegracaoTestBase
 * não é @Transactional por padrão — ver o porquê lá).
 */
@Transactional
class AssuntoErroDominioTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Test
    void nomeDuplicado_devolve409() throws Exception {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Teste NOME_DUPLICADO", TipoPeso.MEDIO));
        criarAssunto(disciplina.getId(), "Assunto Repetido");

        mockMvc.perform(post("/api/assuntos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoAssunto(disciplina.getId(), "assunto repetido")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("NOME_DUPLICADO"));
    }

    @Test
    void disciplinaInexistente_devolve404() throws Exception {
        mockMvc.perform(post("/api/assuntos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoAssunto(999_999L, "Assunto Orfao")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("DISCIPLINA_INEXISTENTE"));
    }

    @Test
    void ordemObrigatoria_devolve422() throws Exception {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Teste ORDEM_OBRIGATORIA", TipoPeso.MEDIO));

        mockMvc.perform(post("/api/assuntos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"disciplinaId": %d, "nome": "Assunto Sem Ordem", "peso": "ALTO", "dificuldadePercebida": 3, "ordem": null}
                    """.formatted(disciplina.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("ORDEM_OBRIGATORIA"))
            .andExpect(jsonPath("$.campo").value("ordem"));
    }

    private void criarAssunto(Long disciplinaId, String nome) throws Exception {
        mockMvc.perform(post("/api/assuntos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoAssunto(disciplinaId, nome)))
            .andExpect(status().isCreated());
    }

    private String corpoAssunto(Long disciplinaId, String nome) {
        return """
            {"disciplinaId": %d, "nome": "%s", "peso": "ALTO", "dificuldadePercebida": 3, "ordem": 1}
            """.formatted(disciplinaId, nome);
    }
}
