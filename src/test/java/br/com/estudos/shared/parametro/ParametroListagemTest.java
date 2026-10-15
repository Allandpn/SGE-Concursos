package br.com.estudos.shared.parametro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.IntegracaoTestBase;

/** As 19 chaves inseridas pelas migrações V4/V5/V6/V10 (docs/SPRINT-15-AJUSTES.md §2, docs/SPRINT-17-USO-REAL.md §2). */
@Transactional
class ParametroListagemTest extends IntegracaoTestBase {

    @Test
    void listar_devolveAs19Chaves() throws Exception {
        mockMvc.perform(get("/api/parametros"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(19));
    }
}
