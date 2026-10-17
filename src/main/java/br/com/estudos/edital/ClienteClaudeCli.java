package br.com.estudos.edital;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * IA pela assinatura do Claude: o Claude Code em modo não interativo, no próprio
 * contêiner (ADR-045, docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3A). É chamada de
 * modelo, não agente: sem ferramentas, um turno, sem sessão guardada. Sem `--bare`,
 * que não lê o token da assinatura.
 */
@Component
public class ClienteClaudeCli {

    static final Duration TEMPO_MAXIMO = Duration.ofMinutes(5);

    /** O que o processo devolveu. Separado para os testes trocarem o processo de verdade. */
    public record Saida(int codigo, String stdout, String stderr) {}

    @FunctionalInterface
    public interface Executor {
        Saida executar(List<String> comando, Map<String, String> ambiente, String entrada, Path pasta) throws IOException;
    }

    private final ConfiguracaoIa configuracao;
    private final JsonMapper jsonMapper;
    private final Executor executor;

    @Autowired
    public ClienteClaudeCli(ConfiguracaoIa configuracao, JsonMapper jsonMapper) {
        this(configuracao, jsonMapper, ClienteClaudeCli::executarProcesso);
    }

    ClienteClaudeCli(ConfiguracaoIa configuracao, JsonMapper jsonMapper, Executor executor) {
        this.configuracao = configuracao;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
    }

    /** Devolve o texto da resposta; qualquer falha vira FalhaIaException com o motivo legível. */
    public String completar(String sistema, String usuario) {
        var token = configuracao.claudeToken();
        if (token == null) {
            throw new FalhaIaException("Token da assinatura do Claude não cadastrado em Ajustes → IA.");
        }
        Path pasta = null;
        try {
            pasta = Files.createTempDirectory("sge-claude-");
            var arquivoSistema = pasta.resolve("sistema.txt");
            Files.writeString(arquivoSistema,
                sistema + "\n\nResponda apenas com o objeto JSON pedido, sem texto antes ou depois.",
                StandardCharsets.UTF_8);
            var comando = List.of(
                configuracao.claudeExecutavel(), "-p",
                "--output-format", "json",
                "--model", configuracao.claudeModelo(),
                "--system-prompt-file", arquivoSistema.toString(),
                "--tools", "",
                "--max-turns", "1",
                "--no-session-persistence",
                "--permission-prompts", "none");
            // O token só existe no ambiente do processo filho; HOME próprio para o
            // Claude Code não misturar estado entre chamadas nem com nada do contêiner.
            var ambiente = Map.of(
                "CLAUDE_CODE_OAUTH_TOKEN", token,
                "HOME", pasta.toString(),
                "DISABLE_AUTOUPDATER", "1");
            return interpretar(executor.executar(comando, ambiente, usuario, pasta));
        } catch (IOException e) {
            throw new FalhaIaException("Não foi possível executar o Claude Code no servidor: " + e.getMessage());
        } finally {
            apagar(pasta);
        }
    }

    String interpretar(Saida saida) {
        String resultado = null;
        int statusHttp = 0;
        boolean erro = saida.codigo() != 0;
        try {
            var json = jsonMapper.readTree(saida.stdout());
            var r = json.path("result");
            if (r.isString()) resultado = r.asString();
            erro = erro || json.path("is_error").asBoolean(false);
            statusHttp = json.path("api_error_status").asInt(0);
        } catch (RuntimeException e) {
            erro = true; // saída que não é JSON: o processo nem chegou a responder
        }
        if (erro) {
            var detalhe = resultado != null && !resultado.isBlank() ? resultado : saida.stderr();
            throw new FalhaIaException(motivo(statusHttp, detalhe));
        }
        if (resultado == null || resultado.isBlank()) {
            throw new FalhaIaException("A assinatura do Claude devolveu uma resposta vazia.");
        }
        return resultado;
    }

    /**
     * Texto curto para o aviso da tela. O status HTTP que o Claude Code relata
     * (`api_error_status`) decide primeiro; o texto só quando ele não veio — e por
     * palavra inteira, não pedaço ("rate" está dentro de "generate").
     */
    static String motivo(int statusHttp, String detalhe) {
        var texto = detalhe == null ? "" : detalhe.strip();
        var minusculo = texto.toLowerCase();
        String base;
        boolean limite = statusHttp == 429 || minusculo.contains("usage limit") || minusculo.contains("rate limit");
        boolean recusado = statusHttp == 401 || statusHttp == 403 || minusculo.contains("authenticat")
            || minusculo.contains("oauth") || minusculo.contains("/login") || minusculo.contains("invalid api key");
        if (limite) {
            base = "Limite de uso da assinatura atingido nesta janela.";
        } else if (recusado) {
            base = "O token da assinatura foi recusado — gere outro com `claude setup-token` e cadastre em Ajustes → IA.";
        } else {
            base = "O Claude Code falhou.";
        }
        if (texto.isEmpty()) return base;
        return base + " (" + (texto.length() > 300 ? texto.substring(0, 300) + "…" : texto) + ")";
    }

    private static Saida executarProcesso(List<String> comando, Map<String, String> ambiente, String entrada, Path pasta)
            throws IOException {
        var construtor = new ProcessBuilder(new ArrayList<>(comando)).directory(pasta.toFile());
        construtor.environment().putAll(ambiente);
        var processo = construtor.start();
        // stdout e stderr lidos em paralelo: um buffer cheio travaria o processo.
        var out = CompletableFuture.supplyAsync(() -> ler(processo.getInputStream()));
        var err = CompletableFuture.supplyAsync(() -> ler(processo.getErrorStream()));
        try (var stdin = processo.getOutputStream()) {
            stdin.write(entrada.getBytes(StandardCharsets.UTF_8));
        }
        try {
            if (!processo.waitFor(TEMPO_MAXIMO.toSeconds(), TimeUnit.SECONDS)) {
                processo.destroyForcibly();
                throw new IOException("sem resposta em " + TEMPO_MAXIMO.toMinutes() + " minutos");
            }
            return new Saida(processo.exitValue(), out.join(), err.join());
        } catch (InterruptedException e) {
            processo.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("interrompido");
        }
    }

    private static String ler(java.io.InputStream entrada) {
        try (entrada) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static void apagar(Path pasta) {
        if (pasta == null) return;
        try (var arquivos = Files.walk(pasta)) {
            arquivos.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignorada) {
            // pasta temporária: o sistema limpa depois
        }
    }
}
