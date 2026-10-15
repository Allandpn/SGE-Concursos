package br.com.estudos.edital;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuração da análise por IA (ADR-043, docs/SPRINT-18-CONCURSOS.md §4.3).
 * A chave vem do `.env` do Pi; sem ela a funcionalidade fica indisponível e
 * nada mais no sistema depende disto.
 */
@Component
public class ConfiguracaoIa {

    private final String chave;
    private final String modelo;
    private final String urlBase;
    private final boolean sincrono;

    public ConfiguracaoIa(
            @Value("${estudos.ia.chave:}") String chave,
            @Value("${estudos.ia.modelo:anthropic/claude-sonnet-5.5}") String modelo,
            @Value("${estudos.ia.url-base:https://openrouter.ai/api/v1}") String urlBase,
            @Value("${estudos.ia.sincrono:false}") boolean sincrono) {
        this.chave = chave;
        this.modelo = modelo;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.sincrono = sincrono;
    }

    public boolean disponivel() {
        return chave != null && !chave.isBlank();
    }

    public String chave() { return chave; }
    public String modelo() { return modelo; }
    public String urlBase() { return urlBase; }
    public boolean sincrono() { return sincrono; }
}
