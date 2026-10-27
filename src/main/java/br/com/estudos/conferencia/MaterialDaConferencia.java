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

    /** Uma parte do assunto que nenhum tema do acervo ensina; {@code decisao}: POR_FORA, IGNORADA ou nulo (ainda sem destino). */
    public record Lacuna(String parte, String motivo, String decisao, Anexo anexo) {}

    /** O PDF que a pessoa anexou a esta parte (D-97): PENDENTE (o worker ainda prepara), PRONTO (virou segmentos) ou FALHOU. */
    public record Anexo(String estado, String nome, Integer paginas, Integer segmentos, Integer minutos, String erro) {}

    public record Info(String chave, String situacao, Integer minutos, Integer fontes, List<Lacuna> lacunas, String consulta, Integer consultaPaginas) {}

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
        var decisoes = new HashMap<Long, Map<String, String>>();   // por id do edital_assunto
        jdbc.query("SELECT d.assunto_edital_id, d.parte, d.decisao FROM edital_lacuna_decisao d JOIN edital_assunto a ON a.id = d.assunto_edital_id "
                + "WHERE a.assunto_id IN (" + marcas + ")",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> decisoes.computeIfAbsent(rs.getLong("assunto_edital_id"), k -> new HashMap<>())
                .put(rs.getString("parte"), rs.getString("decisao")), assuntoIds.toArray());
        var anexos = new HashMap<Long, Map<String, Anexo>>();   // por id do edital_assunto
        jdbc.query("SELECT l.assunto_edital_id, l.parte, l.estado, l.nome_original, l.paginas, l.segmentos, l.minutos, l.erro FROM lacuna_anexo l "
                + "JOIN edital_assunto a ON a.id = l.assunto_edital_id WHERE a.assunto_id IN (" + marcas + ")",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> anexos.computeIfAbsent(rs.getLong("assunto_edital_id"), k -> new HashMap<>())
                .put(rs.getString("parte"), new Anexo(rs.getString("estado"), rs.getString("nome_original"), rs.getInt("paginas"),
                    (Integer) rs.getObject("segmentos"), (Integer) rs.getObject("minutos"), rs.getString("erro"))), assuntoIds.toArray());
        jdbc.query("SELECT id, assunto_id, chave, material_situacao, material_minutos, material_fontes, material_lacunas, material_consulta, "
                + "material_consulta_paginas FROM edital_assunto WHERE assunto_id IN (" + marcas + ") ORDER BY id",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                var decididas = decisoes.getOrDefault(rs.getLong("id"), Map.of());
                var anexados = anexos.getOrDefault(rs.getLong("id"), Map.of());
                var lacunas = new java.util.ArrayList<Lacuna>();
                json.readTree(rs.getString("material_lacunas")).forEach(l -> {
                    var parte = l.path("parte").asString("");
                    lacunas.add(new Lacuna(parte, l.path("motivo").asString(""), decididas.get(parte), anexados.get(parte)));
                });
                saida.put(rs.getLong("assunto_id"), new Info(rs.getString("chave"), rs.getString("material_situacao"),
                    (Integer) rs.getObject("material_minutos"), (Integer) rs.getObject("material_fontes"), lacunas,
                    rs.getString("material_consulta"), (Integer) rs.getObject("material_consulta_paginas")));
            }, assuntoIds.toArray());
        return saida;
    }

    /**
     * A pessoa dá destino a uma lacuna: POR_FORA ("vou estudar por fora") ou IGNORADA; {@code null} desfaz. A parte tem de ser uma das
     * lacunas entregues. Não muda o que o planejamento entregou: só a tela.
     */
    @Transactional
    public Info decidirLacuna(Long assuntoId, String parte, String decisao) {
        if (decisao != null && !List.of("POR_FORA", "IGNORADA").contains(decisao)) {
            throw new br.com.estudos.shared.exception.ValidationException("Decisão desconhecida: " + decisao + ".", "DECISAO_INVALIDA", "decisao");
        }
        var ids = jdbc.queryForList("SELECT id FROM edital_assunto WHERE assunto_id = ? ORDER BY id", Long.class, assuntoId);
        var info = porAssunto(List.of(assuntoId)).get(assuntoId);
        if (ids.isEmpty() || info == null || info.lacunas().stream().noneMatch(l -> l.parte().equals(parte))) {
            throw new br.com.estudos.shared.exception.NotFoundException("Este assunto não tem essa lacuna.", "LACUNA_INEXISTENTE");
        }
        long id = ids.get(0);
        if (decisao == null) {
            jdbc.update("DELETE FROM edital_lacuna_decisao WHERE assunto_edital_id = ? AND parte = ?", id, parte);
        } else {
            jdbc.update("""
                INSERT INTO edital_lacuna_decisao (assunto_edital_id, parte, decisao) VALUES (?, ?, ?)
                ON CONFLICT (assunto_edital_id, parte) DO UPDATE SET decisao = EXCLUDED.decisao, decidida_em = now()""", id, parte, decisao);
        }
        return porAssunto(List.of(assuntoId)).get(assuntoId);
    }
}
