package br.com.estudos.edital;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuração da IA (ADR-043, ADR-044; docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3).
 * Chave e modelo vêm do que foi cadastrado em Ajustes; o que não foi cadastrado
 * cai no ambiente (`OPENROUTER_API_KEY`, `OPENROUTER_MODEL`). Lido a cada uso:
 * mudar em Ajustes vale na próxima chamada, sem reiniciar. Sem chave em lugar
 * nenhum, a IA fica indisponível e nada mais no sistema depende disto.
 */
@Component
public class ConfiguracaoIa {

    public enum Origem { AJUSTES, AMBIENTE }

    private final AjusteIaRepository ajustes;
    private final String chaveAmbiente;
    private final String modeloAmbiente;
    private final String urlBase;
    private final boolean sincrono;

    public ConfiguracaoIa(
            AjusteIaRepository ajustes,
            @Value("${estudos.ia.chave:}") String chaveAmbiente,
            @Value("${estudos.ia.modelo:anthropic/claude-sonnet-5.5}") String modeloAmbiente,
            @Value("${estudos.ia.url-base:https://openrouter.ai/api/v1}") String urlBase,
            @Value("${estudos.ia.sincrono:false}") boolean sincrono) {
        this.ajustes = ajustes;
        this.chaveAmbiente = chaveAmbiente;
        this.modeloAmbiente = modeloAmbiente;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.sincrono = sincrono;
    }

    public boolean disponivel() {
        var chave = chave();
        return chave != null && !chave.isBlank();
    }

    public String chave() {
        var ajustada = ajuste().map(AjusteIa::getChave).filter(c -> !c.isBlank());
        return ajustada.orElse(chaveAmbiente);
    }

    public String modelo() {
        var ajustado = ajuste().map(AjusteIa::getModelo).filter(m -> !m.isBlank());
        return ajustado.orElse(modeloAmbiente);
    }

    /** De onde vem a chave em uso — a tela mostra, para não confundir quem configurou o .env. */
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
