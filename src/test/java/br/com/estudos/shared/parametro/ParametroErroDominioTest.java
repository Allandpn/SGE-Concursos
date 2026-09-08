package br.com.estudos.shared.parametro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.IntegracaoTestBase;

/** Validação única pras 18 chaves — número positivo (docs/SPRINT-15-AJUSTES.md §0). */
@Transactional
class ParametroErroDominioTest extends IntegracaoTestBase {

    @Test
    void chaveInexistente_devolve404() throws Exception {
        mockMvc.perform(patch("/api/parametros/chave_que_nao_existe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"valor": "10"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("PARAMETRO_INEXISTENTE"));
    }

    @Test
    void valorVazio_devolve422() throws Exception {
        mockMvc.perform(patch("/api/parametros/teto_diario_recuperacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"valor": ""}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"))
            .andExpect(jsonPath("$.campo").value("valor"));
    }

    @Test
    void valorNaoNumerico_devolve422() throws Exception {
        mockMvc.perform(patch("/api/parametros/teto_diario_recuperacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"valor": "abc"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));
    }

    @Test
    void valorNegativo_devolve422() throws Exception {
        mockMvc.perform(patch("/api/parametros/teto_diario_recuperacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"valor": "-5"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));
    }

    @Test
    void valorZero_devolve422() throws Exception {
        mockMvc.perform(patch("/api/parametros/teto_diario_recuperacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"valor": "0"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("VALOR_INVALIDO"));
    }
}
