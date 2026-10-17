package br.com.estudos.edital;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuração da IA (ADR-043, ADR-044, ADR-045; docs/SPRINT-19-MATERIAL-AUTOMATICO.md
 * §3 e §3A). O que foi cadastrado em Ajustes vale; o que não foi cai no padrão —
 * ambiente (`OPENROUTER_API_KEY`, `OPENROUTER_MODEL`) para o OpenRouter, Sonnet para o
 * Claude. Lido a cada uso: mudar em Ajustes vale na próxima chamada, sem reiniciar.
 * Sem credencial para o provedor em uso, a IA fica indisponível e nada mais no
 * sistema depende disto.
 */
@Component
public class ConfiguracaoIa {

    public enum Origem { AJUSTES, AMBIENTE }

    public static final Set<String> MODELOS_CLAUDE = Set.of("opus", "sonnet", "haiku");
    public static final String MODELO_CLAUDE_PADRAO = "sonnet";

    public record FalhaClaude(String motivo, Instant em) {}

    private final AjusteIaRepository ajustes;
    private final String chaveAmbiente;
    private final String modeloAmbiente;
    private final String urlBase;
    private final boolean sincrono;
    private final String claudeExecutavel;

    public ConfiguracaoIa(
            AjusteIaRepository ajustes,
            @Value("${estudos.ia.chave:}") String chaveAmbiente,
            @Value("${estudos.ia.modelo:anthropic/claude-sonnet-5.5}") String modeloAmbiente,
            @Value("${estudos.ia.url-base:https://openrouter.ai/api/v1}") String urlBase,
            @Value("${estudos.ia.sincrono:false}") boolean sincrono,
            @Value("${estudos.ia.claude.executavel:claude}") String claudeExecutavel) {
        this.ajustes = ajustes;
        this.chaveAmbiente = chaveAmbiente;
        this.modeloAmbiente = modeloAmbiente;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.sincrono = sincrono;
        this.claudeExecutavel = claudeExecutavel;
    }

    // ------------------------------------------------------------ provedor em uso (ADR-045)

    /** O escolhido em Ajustes; sem escolha, o Claude se houver token, senão o OpenRouter. */
    public ProvedorIa provedor() {
        var escolhido = ajuste().map(AjusteIa::getProvedor);
        if (escolhido.isPresent()) return escolhido.get();
        return claudeConfigurado() ? ProvedorIa.CLAUDE : ProvedorIa.OPENROUTER;
    }

    /** Há credencial para o provedor em uso. */
    public boolean disponivel() {
        return provedor() == ProvedorIa.CLAUDE ? claudeConfigurado() : openrouterConfigurado();
    }

    /** Para registro na análise e para a tela: "claude:sonnet" ou o id do OpenRouter. */
    public String modeloEmUso() {
        return provedor() == ProvedorIa.CLAUDE ? "claude:" + claudeModelo() : modelo();
    }

    // ------------------------------------------------------------ Claude

    public boolean claudeConfigurado() {
        var token = claudeToken();
        return token != null && !token.isBlank();
    }

    public String claudeToken() {
        return ajuste().map(AjusteIa::getClaudeToken).filter(t -> !t.isBlank()).orElse(null);
    }

    public String claudeModelo() {
        return ajuste().map(AjusteIa::getClaudeModelo).filter(MODELOS_CLAUDE::contains).orElse(MODELO_CLAUDE_PADRAO);
    }

    public Optional<FalhaClaude> falhaClaude() {
        return ajuste()
            .filter(a -> a.getClaudeFalhaMotivo() != null)
            .map(a -> new FalhaClaude(a.getClaudeFalhaMotivo(), a.getClaudeFalhaEm()));
    }

    public String claudeExecutavel() { return claudeExecutavel; }

    // ------------------------------------------------------------ OpenRouter

    public boolean openrouterConfigurado() {
        var chave = chave();
        return chave != null && !chave.isBlank();
    }

    /** Chave do OpenRouter. */
    public String chave() {
        var ajustada = ajuste().map(AjusteIa::getChave).filter(c -> !c.isBlank());
        return ajustada.orElse(chaveAmbiente);
    }

    /** Modelo do OpenRouter. */
    public String modelo() {
        var ajustado = ajuste().map(AjusteIa::getModelo).filter(m -> !m.isBlank());
        return ajustado.orElse(modeloAmbiente);
    }

    /** De onde vem a chave do OpenRouter em uso — a tela mostra, para não confundir quem configurou o .env. */
    public Origem origemDaChave() {
        return ajuste().map(AjusteIa::getChave).filter(c -> !c.isBlank()).isPresent()
            ? Origem.AJUSTES : Origem.AMBIENTE;
    }

    public String urlBase() { return urlBase; }
    public boolean sincrono() { return sincrono; }

    private Optional<AjusteIa> ajuste() {
        return ajustes.findById(AjusteIa.UNICA);
    }
}
