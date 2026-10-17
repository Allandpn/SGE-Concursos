package br.com.estudos.edital;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Chamada ao OpenRouter, API compatível com chat completions (ADR-043,
 * docs/SPRINT-18-CONCURSOS.md §4.3). Devolve o texto da resposta do modelo;
 * interpretar o JSON dentro dele é de quem chamou.
 */
@Component
public class ClienteOpenRouter {

    private static final Duration TEMPO_MAXIMO = Duration.ofMinutes(5);

    private final ConfiguracaoIa configuracao;
    private final JsonMapper jsonMapper;
    private final HttpClient http;

    public ClienteOpenRouter(ConfiguracaoIa configuracao, JsonMapper jsonMapper) {
        this.configuracao = configuracao;
        this.jsonMapper = jsonMapper;
        // ProxySelector.getDefault(): respeita https.proxyHost se a rede exigir.
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .proxy(ProxySelector.getDefault())
            .build();
    }

    public String completar(String sistema, String usuario) {
        var corpo = Map.of(
            "model", configuracao.modelo(),
            "temperature", 0,
            "response_format", Map.of("type", "json_object"),
            "messages", List.of(
                Map.of("role", "system", "content", sistema),
                Map.of("role", "user", "content", usuario)));
        var requisicao = HttpRequest.newBuilder(URI.create(configuracao.urlBase() + "/chat/completions"))
            .timeout(TEMPO_MAXIMO)
            .header("Authorization", "Bearer " + configuracao.chave())
            .header("Content-Type", "application/json")
            .header("X-Title", "SGE-Concursos")
            .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(corpo)))
            .build();

        HttpResponse<String> resposta;
        try {
            resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new FalhaIaException("Sem conexão com o OpenRouter. Confira a internet do servidor e tente de novo.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaIaException("A análise foi interrompida.");
        }

        if (resposta.statusCode() / 100 != 2) {
            throw new FalhaIaException(mensagemDeErro(resposta.statusCode(), resposta.body()));
        }
        var conteudo = jsonMapper.readTree(resposta.body())
            .path("choices").path(0).path("message").path("content");
        if (!conteudo.isString() || conteudo.asString().isBlank()) {
            throw new FalhaIaException("A IA devolveu uma resposta vazia. Tente de novo.");
        }
        return conteudo.asString();
    }

    /**
     * Modelos disponíveis no OpenRouter, para a escolha em Ajustes → IA
     * (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3). A listagem é pública: não usa
     * a chave. Sem cache (ADR-029) — a tela é aberta raramente.
     */
    public List<ModeloIa> listarModelos() {
        var requisicao = HttpRequest.newBuilder(URI.create(configuracao.urlBase() + "/models"))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
        HttpResponse<String> resposta;
        try {
            resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new FalhaIaException("Sem conexão com o OpenRouter. Confira a internet do servidor e tente de novo.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FalhaIaException("A consulta foi interrompida.");
        }
        if (resposta.statusCode() / 100 != 2) {
            throw new FalhaIaException(mensagemDeErro(resposta.statusCode(), resposta.body()));
        }
        var modelos = new ArrayList<ModeloIa>();
        for (var m : jsonMapper.readTree(resposta.body()).path("data")) {
            var id = m.path("id").asString("");
            if (id.isBlank()) continue;
            modelos.add(new ModeloIa(id, m.path("name").asString(id),
                porMilhao(m.path("pricing").path("prompt").asString("")),
                porMilhao(m.path("pricing").path("completion").asString(""))));
        }
        modelos.sort(Comparator.comparing(ModeloIa::nome, String.CASE_INSENSITIVE_ORDER));
        return modelos;
    }

    /** O OpenRouter informa preço por token, em texto; a tela mostra por milhão. */
    private static Double porMilhao(String porToken) {
        try {
            return Double.parseDouble(porToken) * 1_000_000;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String mensagemDeErro(int status, String corpo) {
        String detalhe = null;
        try {
            var texto = jsonMapper.readTree(corpo).path("error").path("message");
            if (texto.isString()) detalhe = texto.asString();
        } catch (RuntimeException ignorada) {
            // corpo sem JSON: fica só o status
        }
        var base = switch (status) {
            case 401 -> "A chave do OpenRouter foi recusada. Confira em Ajustes → IA.";
            case 402 -> "Sem crédito na conta do OpenRouter.";
            case 429 -> "Limite de uso do OpenRouter atingido. Tente de novo em instantes.";
            default -> "O OpenRouter respondeu com erro (" + status + ").";
        };
        return detalhe == null ? base : base + " " + detalhe;
    }
}
