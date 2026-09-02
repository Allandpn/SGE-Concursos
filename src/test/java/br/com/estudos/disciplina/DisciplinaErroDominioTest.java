package br.com.estudos.disciplina;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.IntegracaoTestBase;

/**
 * Erros de domínio de disciplina (D-47) — mesmo padrão de
 * AssuntoErroDominioTest: confere status HTTP e codigo do ProblemDetail
 * (ADR-026), não implementação.
 */
@Transactional
class DisciplinaErroDominioTest extends IntegracaoTestBase {

    @Test
    void nomeDuplicado_devolve409() throws Exception {
        criarDisciplina("Disciplina Repetida");

        mockMvc.perform(post("/api/disciplinas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoDisciplina("disciplina repetida")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("NOME_DUPLICADO"));
    }

    private void criarDisciplina(String nome) throws Exception {
        mockMvc.perform(post("/api/disciplinas")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoDisciplina(nome)))
            .andExpect(status().isCreated());
    }

    private String corpoDisciplina(String nome) {
        return """
            {"nome": "%s", "peso": "ALTO"}
            """.formatted(nome);
    }
}
