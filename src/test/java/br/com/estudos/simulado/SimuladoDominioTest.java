package br.com.estudos.simulado;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Erro de domínio (docs/SPRINT-8-SIMULADO.md §6) — D-13: disciplina
 * inexistente, disciplina duplicada no mesmo simulado, lista de resultados
 * vazia. Mesmo padrão de AssuntoErroDominioTest/ErroDominioTest.
 */
@Transactional
class SimuladoDominioTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    private Disciplina novaDisciplina() {
        return disciplinaService.criar(new DisciplinaRequest("Disciplina Simulado " + UUID.randomUUID(), TipoPeso.MEDIO));
    }

    @Test
    void disciplinaInexistente_devolve404() throws Exception {
        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-06-01", "duracaoMinutos": 240,
                     "resultados": [{"disciplinaId": 999999, "formato": "MULTIPLA_ESCOLHA", "questoesCorretas": 10, "questoesTotal": 20}]}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("DISCIPLINA_INEXISTENTE"));
    }

    @Test
    void disciplinaDuplicadaNoMesmoSimulado_devolve409() throws Exception {
        var disciplina = novaDisciplina();

        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-06-01", "duracaoMinutos": 240,
                     "resultados": [
                       {"disciplinaId": %d, "formato": "MULTIPLA_ESCOLHA", "questoesCorretas": 10, "questoesTotal": 20},
                       {"disciplinaId": %d, "formato": "CERTO_ERRADO", "questoesCorretas": 5, "questoesTotal": 10}
                     ]}
                    """.formatted(disciplina.getId(), disciplina.getId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("DISCIPLINA_DUPLICADA_NO_SIMULADO"));
    }

    @Test
    void resultadosVazio_devolve422() throws Exception {
        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-06-01", "duracaoMinutos": 240, "resultados": []}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("RESULTADOS_OBRIGATORIOS"));
    }

    @Test
    void dataAusente_devolve422() throws Exception {
        var disciplina = novaDisciplina();

        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"duracaoMinutos": 240,
                     "resultados": [{"disciplinaId": %d, "formato": "MULTIPLA_ESCOLHA", "questoesCorretas": 10, "questoesTotal": 20}]}
                    """.formatted(disciplina.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("DATA_OBRIGATORIA"));
    }

    @Test
    void duracaoAusente_devolve422() throws Exception {
        var disciplina = novaDisciplina();

        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-06-01",
                     "resultados": [{"disciplinaId": %d, "formato": "MULTIPLA_ESCOLHA", "questoesCorretas": 10, "questoesTotal": 20}]}
                    """.formatted(disciplina.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("DURACAO_OBRIGATORIA"));
    }

    @Test
    void formatoDoResultadoAusente_devolve422() throws Exception {
        var disciplina = novaDisciplina();

        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-06-01", "duracaoMinutos": 240,
                     "resultados": [{"disciplinaId": %d, "questoesCorretas": 10, "questoesTotal": 20}]}
                    """.formatted(disciplina.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("FORMATO_OBRIGATORIO"));
    }

    @Test
    void questoesDoResultadoAusente_devolve422() throws Exception {
        var disciplina = novaDisciplina();

        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-06-01", "duracaoMinutos": 240,
                     "resultados": [{"disciplinaId": %d, "formato": "MULTIPLA_ESCOLHA"}]}
                    """.formatted(disciplina.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("QUESTOES_OBRIGATORIAS"));
    }

    @Test
    void listar_devolveTodosOsSimuladosComSeusResultados() throws Exception {
        var disciplinaA = novaDisciplina();
        var disciplinaB = novaDisciplina();

        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-06-01", "duracaoMinutos": 240,
                     "resultados": [{"disciplinaId": %d, "formato": "MULTIPLA_ESCOLHA", "questoesCorretas": 10, "questoesTotal": 20}]}
                    """.formatted(disciplinaA.getId())))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-07-01", "duracaoMinutos": 180,
                     "resultados": [{"disciplinaId": %d, "formato": "CERTO_ERRADO", "questoesCorretas": 5, "questoesTotal": 10}]}
                    """.formatted(disciplinaB.getId())))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/simulados"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].resultados.length()").value(1))
            .andExpect(jsonPath("$[1].resultados.length()").value(1));
    }

    @Test
    void registraDuasDisciplinas_devolve201ComOsDoisResultados() throws Exception {
        var disciplinaA = novaDisciplina();
        var disciplinaB = novaDisciplina();

        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-06-01", "duracaoMinutos": 240,
                     "resultados": [
                       {"disciplinaId": %d, "formato": "MULTIPLA_ESCOLHA", "questoesCorretas": 10, "questoesTotal": 20},
                       {"disciplinaId": %d, "formato": "CERTO_ERRADO", "questoesCorretas": 5, "questoesTotal": 10}
                     ]}
                    """.formatted(disciplinaA.getId(), disciplinaB.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.duracaoMinutos").value(240))
            .andExpect(jsonPath("$.resultados.length()").value(2));
    }
}
