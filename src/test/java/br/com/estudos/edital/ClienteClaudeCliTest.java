package br.com.estudos.edital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * ADR-045 — a chamada ao Claude Code (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3A),
 * com um processo falso no lugar do `claude`: as flags certas, o token só no
 * ambiente do processo filho, e a falha virando motivo legível.
 */
class ClienteClaudeCliTest {

    private final List<List<String>> comandos = new ArrayList<>();
    private final List<Map<String, String>> ambientes = new ArrayList<>();
    private final List<String> entradas = new ArrayList<>();
    private final List<String> sistemas = new ArrayList<>();

    private ClienteClaudeCli cliente(String token, ClienteClaudeCli.Saida saida) {
        var repositorio = mock(AjusteIaRepository.class);
        var ajuste = new AjusteIa(java.time.Instant.EPOCH);
        ajuste.setClaudeToken(token);
        ajuste.setClaudeModelo("opus");
        when(repositorio.findById(AjusteIa.UNICA)).thenReturn(Optional.of(ajuste));
        var configuracao = new ConfiguracaoIa(repositorio, "", "x", "http://localhost", true, "/usr/bin/claude");
        return new ClienteClaudeCli(configuracao, JsonMapper.builder().build(), (comando, ambiente, entrada, pasta) -> {
            comandos.add(comando);
            ambientes.add(ambiente);
            entradas.add(entrada);
            var i = comando.indexOf("--system-prompt-file");
            sistemas.add(Files.readString(java.nio.file.Path.of(comando.get(i + 1))));
            return saida;
        });
    }

    @Test
    void chamadaDeModelo_semFerramentas_tokenSoNoAmbiente() {
        var c = cliente("sk-ant-oat-token-de-teste",
            new ClienteClaudeCli.Saida(0, "{\"type\":\"result\",\"is_error\":false,\"result\":\"{\\\"ok\\\":true}\"}", ""));

        assertEquals("{\"ok\":true}", c.completar("SISTEMA", "PEDIDO"));

        var comando = comandos.get(0);
        assertEquals("/usr/bin/claude", comando.get(0));
        assertTrue(comando.containsAll(List.of("-p", "--output-format", "json", "--no-session-persistence")));
        assertEquals("opus", comando.get(comando.indexOf("--model") + 1));
        assertEquals("", comando.get(comando.indexOf("--tools") + 1), "sem ferramentas: chamada de modelo, não agente");
        assertEquals("1", comando.get(comando.indexOf("--max-turns") + 1));
        assertFalse(comando.contains("--bare"), "o modo bare não lê o token da assinatura");
        assertFalse(String.join(" ", comando).contains("sk-ant"), "o token nunca vai na linha de comando");
        assertEquals("sk-ant-oat-token-de-teste", ambientes.get(0).get("CLAUDE_CODE_OAUTH_TOKEN"));
        assertEquals("PEDIDO", entradas.get(0), "o pedido vai pela entrada padrão");
        assertTrue(sistemas.get(0).startsWith("SISTEMA"));
    }

    @Test
    void limiteDaJanela_viraMotivoLegivel() {
        var c = cliente("sk-ant-oat-token-de-teste",
            new ClienteClaudeCli.Saida(1, "{\"is_error\":true,\"result\":\"Claude AI usage limit reached|1760000000\"}", ""));

        var erro = assertThrows(FalhaIaException.class, () -> c.completar("s", "u"));
        assertTrue(erro.getMessage().startsWith("Limite de uso da assinatura atingido"));
    }

    @Test
    void tokenRecusado_dizComoResolver() {
        var c = cliente("sk-ant-oat-token-de-teste",
            new ClienteClaudeCli.Saida(1, "{\"is_error\":true,\"result\":\"Invalid API key · Please run /login\"}", ""));

        var erro = assertThrows(FalhaIaException.class, () -> c.completar("s", "u"));
        assertTrue(erro.getMessage().contains("claude setup-token"));
    }

    /** Saída real do Claude Code 2.1.292 com token inválido (conferida no contêiner do app). */
    @Test
    void saidaRealDeTokenInvalido_viraMotivoDeTokenRecusado() {
        var real = "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":true,\"api_error_status\":401,"
            + "\"terminal_reason\":\"api_error\",\"num_turns\":1,"
            + "\"result\":\"Failed to authenticate. API Error: 401 OAuth access token is invalid.\"}";
        var c = cliente("sk-ant-oat-token-de-teste", new ClienteClaudeCli.Saida(1, real, ""));

        var erro = assertThrows(FalhaIaException.class, () -> c.completar("s", "u"));
        assertTrue(erro.getMessage().startsWith("O token da assinatura foi recusado"));
    }

    @Test
    void palavraDentroDeOutra_naoEnganaAClassificacao() {
        assertTrue(ClienteClaudeCli.motivo(500, "could not generate a response").startsWith("O Claude Code falhou"));
        assertTrue(ClienteClaudeCli.motivo(429, "").startsWith("Limite de uso"));
    }

    @Test
    void saidaQueNaoEJson_falha() {
        var c = cliente("sk-ant-oat-token-de-teste", new ClienteClaudeCli.Saida(127, "", "claude: not found"));

        var erro = assertThrows(FalhaIaException.class, () -> c.completar("s", "u"));
        assertTrue(erro.getMessage().contains("claude: not found"));
    }

    @Test
    void semToken_naoChamaOProcesso() {
        var c = cliente(null, new ClienteClaudeCli.Saida(0, "{}", ""));

        assertThrows(FalhaIaException.class, () -> c.completar("s", "u"));
        assertTrue(comandos.isEmpty());
    }
}
