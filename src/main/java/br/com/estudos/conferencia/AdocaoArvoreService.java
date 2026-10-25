package br.com.estudos.conferencia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.arvore.ArvoreResponses.Versao;
import br.com.estudos.arvore.ArvoreService;
import br.com.estudos.shared.exception.NotFoundException;

/**
 * Atualizar a árvore do QConcursos é um gesto da pessoa (01_DOMINIO §3.12, D-90; docs/SPRINT-25-CONFERENCIA-EDITAL.md §8).
 * Antes de adotar uma versão nova, mostra-se o impacto; adotar nunca troca um nó sem ela ver: a parte cujo nó saiu ou
 * mudou de pai volta à fila e espera ser reconfirmada, e a que não foi tocada passa para a versão nova.
 */
@Service
public class AdocaoArvoreService {

    public record ParteAfetada(Long parteId, Long analiseId, String parte, Long noId, String tipo) {}

    public record Impacto(
        Versao versao, Versao atual, boolean adotada, int partesAfetadas, int partesRenomeadas,
        Map<String, Integer> porTipo, Map<Long, Integer> porEdital, int decisoesAfetadas, List<ParteAfetada> exemplos) {
        Impacto comoAdotada() {
            return new Impacto(versao, atual, true, partesAfetadas, partesRenomeadas, porTipo, porEdital, decisoesAfetadas, exemplos);
        }
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final ArvoreService arvore;

    public AdocaoArvoreService(JdbcTemplate jdbc, JsonMapper json, ArvoreService arvore) {
        this.jdbc = jdbc;
        this.json = json;
        this.arvore = arvore;
    }

    @Transactional(readOnly = true)
    public Impacto impacto(long versaoId) {
        return calcular(versaoId, false).impacto();
    }

    /** Sem {@code confirmar}, só mostra o impacto; com ele, adota. */
    @Transactional
    public Impacto adotar(long versaoId, boolean confirmar) {
        var calculo = calcular(versaoId, confirmar);
        if (!confirmar || calculo.impacto().atual() != null && calculo.impacto().atual().id() == versaoId) return calculo.impacto();
        var nova = versaoId;
        var porAnalise = new TreeMap<Long, int[]>();
        for (var parte : calculo.partes()) {
            if (parte.nosQueMudaram().isEmpty()) {
                jdbc.update("UPDATE edital_parte SET arvore_versao_id = ? WHERE id = ?", nova, parte.id());
            } else {
                jdbc.update("UPDATE edital_parte SET no_mudou = true WHERE id = ?", parte.id());
                porAnalise.computeIfAbsent(parte.analiseId(), k -> new int[1])[0]++;
            }
        }
        arvore.tornarVigente(nova);
        var rotulo = arvore.versao(nova).rotulo();
        porAnalise.forEach((analise, n) -> jdbc.update(
            "INSERT INTO edital_evento (analise_id, parte_id, tipo, antes, depois) VALUES (?, NULL, 'ARVORE_ADOTADA', NULL, ?)",
            analise, json.writeValueAsString(Map.of("arvore", rotulo, "partesQueVoltaramAFila", n[0]))));
        return calculo.impacto().comoAdotada();
    }

    // ---------------------------------------------------------------- cálculo

    private record ParteVista(long id, long analiseId, String texto, long versaoId, List<Long> nos, List<Long> nosQueMudaram) {}

    private record Calculo(Impacto impacto, List<ParteVista> partes) {}

    private Calculo calcular(long versaoId, boolean confirmar) {
        var nova = arvore.versao(versaoId);
        var vigenteId = arvore.idDaVigente();
        var atual = vigenteId == null ? null : arvore.versao(vigenteId);
        if (vigenteId != null && vigenteId == versaoId) {
            return new Calculo(new Impacto(nova, atual, false, 0, 0, tipos(), new LinkedHashMap<>(), 0, List.of()), List.of());
        }

        var nosPorParte = new java.util.HashMap<Long, List<Long>>();
        jdbc.query("SELECT parte_id, no_id FROM edital_parte_no WHERE NOT proposto ORDER BY parte_id, ordem",
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                nosPorParte.computeIfAbsent(rs.getLong("parte_id"), k -> new ArrayList<>()).add(rs.getLong("no_id")));
        var partes = new ArrayList<ParteVista>();
        var porTipo = tipos();
        var porEdital = new LinkedHashMap<Long, Integer>();
        var exemplos = new ArrayList<ParteAfetada>();
        int renomeadas = 0;
        var linhas = jdbc.queryForList("""
            SELECT p.id, d.analise_id, p.texto, p.arvore_versao_id
              FROM edital_parte p
              JOIN edital_assunto a ON a.id = p.assunto_edital_id
              JOIN edital_disciplina d ON d.id = a.disciplina_id
             WHERE p.aparece AND p.estado <> 'DESCARTADA' AND p.arvore_versao_id <> ? AND NOT p.no_mudou
             ORDER BY d.analise_id, p.id""", versaoId);
        for (var l : linhas) {
            long id = ((Number) l.get("id")).longValue();
            long analise = ((Number) l.get("analise_id")).longValue();
            long deVersao = ((Number) l.get("arvore_versao_id")).longValue();
            var nos = nosPorParte.getOrDefault(id, List.of());
            var mudaram = new ArrayList<Long>();
            boolean renomeou = false;
            for (var no : nos) {
                var tipo = arvore.tipoDeMudanca(no, deVersao, versaoId);
                if (tipo == null) continue;
                if ("RENOMEADO".equals(tipo)) {
                    renomeou = true;
                } else {
                    mudaram.add(no);
                    porTipo.merge(tipo, 1, Integer::sum);
                    if (exemplos.size() < 20) exemplos.add(new ParteAfetada(id, analise, (String) l.get("texto"), no, tipo));
                }
            }
            if (renomeou) renomeadas++;
            if (!mudaram.isEmpty()) porEdital.merge(analise, 1, Integer::sum);
            partes.add(new ParteVista(id, analise, (String) l.get("texto"), deVersao, nos, mudaram));
        }
        int afetadas = porEdital.values().stream().mapToInt(Integer::intValue).sum();

        int decisoes = 0;
        for (var l : jdbc.queryForList("SELECT nos, arvore_versao_id FROM edital_decisao WHERE ativa AND arvore_versao_id <> ?", versaoId)) {
            long de = ((Number) l.get("arvore_versao_id")).longValue();
            boolean mudou = false;
            for (var no : json.readTree((String) l.get("nos"))) {
                var tipo = arvore.tipoDeMudanca(no.asLong(), de, versaoId);
                if ("REMOVIDO".equals(tipo) || "MOVIDO".equals(tipo)) mudou = true;
            }
            if (mudou) decisoes++;
        }
        return new Calculo(new Impacto(nova, atual, false, afetadas, renomeadas, porTipo, porEdital, decisoes, exemplos), partes);
    }

    private static Map<String, Integer> tipos() {
        var m = new LinkedHashMap<String, Integer>();
        m.put("REMOVIDO", 0);
        m.put("MOVIDO", 0);
        return m;
    }

    /** Nome do que falta quando a versão não existe. */
    static NotFoundException semVersao() {
        return new NotFoundException("Versão da árvore não encontrada.", "ARVORE_VERSAO_INEXISTENTE");
    }
}
