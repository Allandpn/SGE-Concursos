package br.com.estudos.acesso;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** D-112 pelo caminho HTTP: o bloqueio responde 429 e diz quando volta. */
class FreioHttpTest extends AcessoTestBase {

    @Test
    void d112_codigoErradoTresVezes_bloqueia_eDizQuandoVolta() throws Exception {
        cadastrarPrimeiro(new AutenticadorFalso(1L));
        for (int i = 0; i < 3; i++) {
            mvc.perform(publico(post("/api/acesso/recuperar"), "198.51.100.1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"AAAA-AAAA-AAAA\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(publico(post("/api/acesso/recuperar"), "198.51.100.1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + ultimosCodigos.get(0).asString() + "\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.codigo").value("MUITAS_TENTATIVAS"))
            .andExpect(jsonPath("$.ate").exists());
        RELOGIO.avancar(Duration.ofHours(1).plusSeconds(1));
        mvc.perform(publico(post("/api/acesso/recuperar"), "198.51.100.1").contentType(MediaType.APPLICATION_JSON)
            .content("{\"codigo\":\"" + ultimosCodigos.get(0).asString() + "\"}")).andExpect(status().isNoContent());
    }
}
