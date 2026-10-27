package br.com.estudos.conferencia;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.segmento.MaterialService;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * O material por casamento, do lado do SGE (01_DOMINIO §3.10, D-65, D-94; docs/SPRINT-26 §6): diz ao planejamento quais assuntos
 * de uma conferência concluída ainda esperam material, com as partes que a pessoa conferiu, e guarda o que ele entrega. Os
 * segmentos seguem o caminho de sempre (importação por chave externa); o SGE nunca apaga segmento (D-18).
 */
@Service
public class EditalMaterialService {

    private static final Set<String> SITUACOES = Set.of("COMPLETO", "PARCIAL", "SEM_MATERIAL", "RECUSADO");

    public record ParteConferida(String texto, String contexto, String herdado, String natureza, List<Long> nos) {}

    public record AssuntoPendente(String chave, String chaveExterna, String disciplina, String texto, boolean temSegmentos,
                                  List<ParteConferida> partes) {}

    public record MaterialPendente(Long analiseId, String slug, String cargo, String concursoNome, List<AssuntoPendente> assuntos) {}

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final MaterialService material;
    private final br.com.estudos.push.AvisoDeProcesso avisos;

    public EditalMaterialService(JdbcTemplate jdbc, JsonMapper json, MaterialService material, br.com.estudos.push.AvisoDeProcesso avisos) {
        this.avisos = avisos;
        this.jdbc = jdbc;
        this.json = json;
        this.material = material;
    }

    /** O edital concluído mais antigo que ainda tem assunto sem material entregue; vazio se nenhum. */
    @Transactional(readOnly = true)
    public List<MaterialPendente> pendentes() {
        var analises = jdbc.queryForList("""
            SELECT DISTINCT d.analise_id FROM edital_assunto a JOIN edital_disciplina d ON d.id = a.disciplina_id
              JOIN analise_edital e ON e.id = d.analise_id
             WHERE e.status = 'CONFIRMADA' AND a.assunto_id IS NOT NULL AND a.aparece AND a.material_situacao IS NULL
             ORDER BY d.analise_id LIMIT 1""", Long.class);
        if (analises.isEmpty()) return List.of();
        long analiseId = analises.get(0);
        var dados = ConferenciaDados.ler(jdbc, analiseId);
        var info = jdbc.queryForMap("""
            SELECT e.concurso_nome, e.cargo_escolhido, c.slug FROM analise_edital e LEFT JOIN concurso c ON c.id = e.concurso_id WHERE e.id = ?""", analiseId);
        var chavesExternas = new java.util.HashMap<Long, String>();
        jdbc.query("SELECT a.id, a.chave_externa FROM assunto a WHERE a.id IN (SELECT assunto_id FROM edital_assunto WHERE assunto_id IS NOT NULL)",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> chavesExternas.put(rs.getLong("id"), rs.getString("chave_externa")));
        var ids = dados.assuntos.stream().map(ConferenciaDados.Ass::assuntoId).filter(java.util.Objects::nonNull).toList();
        var estados = material.estados(ids);
        var saida = new ArrayList<AssuntoPendente>();
        for (var d : dados.disciplinas) {
            for (var a : dados.assuntosDe(d.id())) {
                if (!a.aparece() || a.assuntoId() == null) continue;
                var pendente = jdbc.queryForObject("SELECT material_situacao IS NULL FROM edital_assunto WHERE id = ?", Boolean.class, a.id());
                var estado = estados.get(a.assuntoId());
                // Assunto com lacuna registrada (D-65) já foi resolvido pelo caminho antigo: não se refaz sem "Refazer o material".
                if (!pendente || (estado != null && estado.lacuna() != null)) continue;
                var partes = new ArrayList<ParteConferida>();
                for (var p : dados.partesDe(a.id())) {
                    if (!p.ativa()) continue;
                    partes.add(new ParteConferida(p.texto(), p.contexto(), p.herdado(), p.natureza(), p.nos()));
                }
                saida.add(new AssuntoPendente(a.chave(), chavesExternas.get(a.assuntoId()), d.nome(), a.texto(),
                    estado != null && estado.segmentos() > 0, partes));
            }
        }
        return saida.isEmpty() ? List.of()
            : List.of(new MaterialPendente(analiseId, (String) info.get("slug"), (String) info.get("cargo_escolhido"),
                (String) info.get("concurso_nome"), saida));
    }

    /** Guarda o que o planejamento entregou de cada assunto; idempotente (a última entrega vale). */
    @Transactional
    public int entregar(Long analiseId, JsonNode corpo) {
        int n = 0;
        for (var a : corpo.path("assuntos")) {
            var chave = a.path("chave").asString("");
            var situacao = a.path("situacao").asString("");
            if (!SITUACOES.contains(situacao)) {
                throw new ValidationException("Situação desconhecida: " + situacao + ".", "SITUACAO_INVALIDA", "situacao");
            }
            var consulta = a.path("consulta");
            int atualizados = jdbc.update("""
                UPDATE edital_assunto SET material_situacao = ?, material_minutos = ?, material_fontes = ?, material_lacunas = ?,
                       material_consulta = ?, material_consulta_paginas = ?, material_em = now()
                 WHERE chave = ? AND disciplina_id IN (SELECT id FROM edital_disciplina WHERE analise_id = ?)""",
                situacao, a.path("minutos").isNumber() ? a.get("minutos").asInt() : null,
                a.path("fontes").isNumber() ? a.get("fontes").asInt() : null,
                json.writeValueAsString(a.path("lacunas").isArray() ? a.get("lacunas") : json.createArrayNode()),
                consulta.path("arquivo").isString() ? consulta.get("arquivo").asString() : null,
                consulta.path("paginas").isNumber() ? consulta.get("paginas").asInt() : null, chave, analiseId);
            if (atualizados == 0) throw new NotFoundException("O assunto " + chave + " não é desta análise.", "ASSUNTO_INEXISTENTE");
            n += atualizados;
        }
        avisos.materialPronto(analiseId);
        return n;
    }
}
