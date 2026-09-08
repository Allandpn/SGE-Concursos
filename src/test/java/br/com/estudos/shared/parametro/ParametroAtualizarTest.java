package br.com.estudos.shared.parametro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.IntegracaoTestBase;

@Transactional
class ParametroAtualizarTest extends IntegracaoTestBase {

    @Test
    void atualizar_gravaOValorNovo() throws Exception {
        mockMvc.perform(patch("/api/parametros/teto_diario_recuperacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"valor": "12"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.chave").value("teto_diario_recuperacoes"))
            .andExpect(jsonPath("$.valor").value("12"));
    }
}
