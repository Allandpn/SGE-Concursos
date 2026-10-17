package br.com.estudos.edital;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.shared.IntegracaoTestBase;

/**
 * ADR-045 (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3A): a assinatura do Claude é o
 * provedor em uso quando há token; se ela parar, o motivo vira aviso e **nada troca
 * sozinho** — o proxy responde IA_PAUSADA (o worker espera, sem lacuna) até o
 * usuário escolher o OpenRouter ou o Claude voltar a responder.
 *
 * Sem @Transactional: o roteador grava a falha fora da transação (precisa sobreviver
 * ao erro). A configuração é apagada depois de cada caso para não vazar.
 */
class IaAssinaturaTest extends IntegracaoTestBase {

    private static final String TOKEN = "token-interno-de-teste";
    private static final HttpServer OPENROUTER;

    static {
        try {
            OPENROUTER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        var json = JsonMapper.builder().build();
        OPENROUTER.createContext("/api/v1/chat/completions", troca -> {
            var bytes = json.writeValueAsString(Map.of("choices",
                List.of(Map.of("message", Map.of("content", "{\"de\":\"openrouter\"}"))))).getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        OPENROUTER.start();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("estudos.ia.chave", () -> "chave-openrouter-do-ambiente");
        registro.add("estudos.ia.url-base", () -> "http://127.0.0.1:" + OPENROUTER.getAddress().getPort() + "/api/v1");
        registro.add("estudos.interno.token", () -> TOKEN);
    }

    @MockitoBean private ClienteClaudeCli claude;
    @Autowired private AjusteIaRepository ajustes;

    @AfterEach
    void limpar() {
        ajustes.deleteAll();
        reset(claude);
    }

    private void cadastrarToken() throws Exception {
        mockMvc.perform(put("/api/ia/configuracao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"claudeToken\": \"sk-ant-oat01-assinatura-5678\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provedor").value("CLAUDE"))
            .andExpect(jsonPath("$.claude.tokenFinal").value("5678"))
            .andExpect(jsonPath("$.claude.modelo").value("sonnet"));
    }

    private org.springframework.test.web.servlet.ResultActions proxy() throws Exception {
        return mockMvc.perform(post("/internal/ia/completar").header("X-Internal-Token", TOKEN)
            .contentType(MediaType.APPLICATION_JSON).content("{\"sistema\": \"s\", \"usuario\": \"u\"}"));
    }

    @Test
    void comToken_oClaudeResponde() throws Exception {
        cadastrarToken();
        when(claude.completar(anyString(), anyString())).thenReturn("{\"de\":\"claude\"}");

        proxy().andExpect(status().isOk())
            .andExpect(jsonPath("$.conteudo").value("{\"de\":\"claude\"}"))
            .andExpect(jsonPath("$.modelo").value("claude:sonnet"));
    }

    @Test
    void assinaturaParou_avisaENaoTrocaSozinho() throws Exception {
        cadastrarToken();
        when(claude.completar(anyString(), anyString()))
            .thenThrow(new FalhaIaException("Limite de uso da assinatura atingido nesta janela."));

        proxy().andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.codigo").value("IA_PAUSADA"));

        mockMvc.perform(get("/api/ia/configuracao"))
            .andExpect(jsonPath("$.provedor").value("CLAUDE")) // nunca troca sozinho
            .andExpect(jsonPath("$.claude.falha.motivo").value("Limite de uso da assinatura atingido nesta janela."));
    }

    @Test
    void claudeVoltando_apagaOAviso() throws Exception {
        cadastrarToken();
        when(claude.completar(anyString(), anyString()))
            .thenThrow(new FalhaIaException("Limite de uso da assinatura atingido nesta janela."))
            .thenReturn("{}");

        proxy().andExpect(status().isServiceUnavailable());
        proxy().andExpect(status().isOk());

        mockMvc.perform(get("/api/ia/configuracao"))
            .andExpect(jsonPath("$.claude.falha").doesNotExist());
    }

    @Test
    void usuarioEscolheOpenRouter_eDepoisVoltaAoClaude() throws Exception {
        cadastrarToken();
        when(claude.completar(anyString(), anyString()))
            .thenThrow(new FalhaIaException("Limite de uso da assinatura atingido nesta janela."));
        proxy().andExpect(status().isServiceUnavailable());

        mockMvc.perform(put("/api/ia/configuracao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"provedor\": \"OPENROUTER\"}"))
            .andExpect(jsonPath("$.provedor").value("OPENROUTER"));
        reset(claude);
        proxy().andExpect(status().isOk())
            .andExpect(jsonPath("$.conteudo").value("{\"de\":\"openrouter\"}"));
        verify(claude, never()).completar(anyString(), anyString());

        // "Tentar o Claude de novo": volta e o aviso some até a próxima falha.
        mockMvc.perform(put("/api/ia/configuracao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"provedor\": \"CLAUDE\"}"))
            .andExpect(jsonPath("$.provedor").value("CLAUDE"))
            .andExpect(jsonPath("$.claude.falha").doesNotExist());
    }

    @Test
    void escolherClaudeSemToken_recusa() throws Exception {
        mockMvc.perform(put("/api/ia/configuracao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"provedor\": \"CLAUDE\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("CLAUDE_SEM_TOKEN"));
    }

    @Test
    void modeloDoClaudeForaDaLista_recusa() throws Exception {
        mockMvc.perform(put("/api/ia/configuracao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"claudeModelo\": \"gpt-5\"}"))
            .andExpect(status().isUnprocessableEntity());
    }
}
