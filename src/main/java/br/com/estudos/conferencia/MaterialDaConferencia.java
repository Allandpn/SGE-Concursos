package br.com.estudos.conferencia;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * O que o planejamento entregou do material de um assunto que nasceu de uma conferência (01_DOMINIO §3.10; docs/SPRINT-26 §6):
 * a situação do casamento, o tempo estimado, as lacunas por parte e o PDF de consulta. A situação dos segmentos em si continua
 * derivada deles (D-16); aqui ficam só os fatos que os segmentos não dizem.
 */
@Component
public class MaterialDaConferencia {

    public record Info(String chave, String situacao, Integer minutos, Integer fontes, List<String> lacunas, String consulta, Integer consultaPaginas) {}

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public MaterialDaConferencia(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** Por id do assunto cadastrado; assunto que não veio de conferência não aparece no mapa. */
    @Transactional(readOnly = true)
    public Map<Long, Info> porAssunto(Collection<Long> assuntoIds) {
        var saida = new HashMap<Long, Info>();
        if (assuntoIds.isEmpty()) return saida;
        var marcas = String.join(",", assuntoIds.stream().map(x -> "?").toList());
        jdbc.query("SELECT assunto_id, chave, material_situacao, material_minutos, material_fontes, material_lacunas, material_consulta, "
                + "material_consulta_paginas FROM edital_assunto WHERE assunto_id IN (" + marcas + ") ORDER BY id",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                var lacunas = new java.util.ArrayList<String>();
                json.readTree(rs.getString("material_lacunas")).forEach(l -> {
                    var parte = l.path("parte").asString("");
                    var motivo = l.path("motivo").asString("");
                    lacunas.add(motivo.isBlank() ? parte : parte + ": " + motivo);
                });
                saida.put(rs.getLong("assunto_id"), new Info(rs.getString("chave"), rs.getString("material_situacao"),
                    (Integer) rs.getObject("material_minutos"), (Integer) rs.getObject("material_fontes"), lacunas,
                    rs.getString("material_consulta"), (Integer) rs.getObject("material_consulta_paginas")));
            }, assuntoIds.toArray());
        return saida;
    }
}
