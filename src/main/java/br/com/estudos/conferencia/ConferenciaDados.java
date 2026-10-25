package br.com.estudos.conferencia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

/**
 * Tudo o que a conferência de uma análise guarda, lido de uma vez para a memória (centenas de partes): o resumo, a árvore
 * do edital e a fila de revisão são derivados daqui (D-16), nunca persistidos.
 */
final class ConferenciaDados {

    record Disc(long id, int ordem, String nome, String restricao, boolean comum, Integer questoes, String pesoPessoa,
                String qconcursos, String texto, String referencias, int tentativas) {}

    record Ass(long id, long disciplinaId, String chave, int ordem, String texto, String erros, String alertas,
               boolean aceito, boolean aparece, Long assuntoId) {}

    record Parte(long id, long assuntoEditalId, String chave, int ordem, String texto, String contexto, String herdado,
                 String natureza, String origem, String estado, long arvoreVersaoId, boolean aparece, boolean noMudou,
                 String comentario, String propNatureza, boolean iaMudou, List<Long> nos, List<Long> propostos) {
        boolean ativa() {
            return aparece && !"DESCARTADA".equals(estado);
        }

        String nome() {
            return herdado == null || herdado.isBlank() ? texto : herdado + " " + texto;
        }
    }

    record Trecho(long id, Long disciplinaId, String tipo, String estado, String motivo, String linhas) {}

    final long analiseId;
    final List<Disc> disciplinas = new ArrayList<>();
    final List<Ass> assuntos = new ArrayList<>();
    final List<Parte> partes = new ArrayList<>();
    final List<Trecho> trechos = new ArrayList<>();
    final Map<Long, Disc> disciplinaPorId = new HashMap<>();
    final Map<Long, Ass> assuntoPorId = new HashMap<>();

    private ConferenciaDados(long analiseId) {
        this.analiseId = analiseId;
    }

    static ConferenciaDados ler(JdbcTemplate jdbc, long analiseId) {
        var d = new ConferenciaDados(analiseId);
        consultar(jdbc, """
            SELECT id, ordem, nome, restricao, comum, questoes, peso_pessoa, qconcursos_ids, texto, referencias, tentativas
              FROM edital_disciplina WHERE analise_id = ? ORDER BY ordem, id""",
            rs -> {
                var x = new Disc(rs.getLong("id"), rs.getInt("ordem"), rs.getString("nome"), rs.getString("restricao"),
                    rs.getBoolean("comum"), (Integer) rs.getObject("questoes"), rs.getString("peso_pessoa"),
                    rs.getString("qconcursos_ids"), rs.getString("texto"), rs.getString("referencias"), rs.getInt("tentativas"));
                d.disciplinas.add(x);
                d.disciplinaPorId.put(x.id(), x);
            }, analiseId);
        consultar(jdbc, """
            SELECT a.id, a.disciplina_id, a.chave, a.ordem, a.texto, a.erros, a.alertas, a.aceito, a.aparece, a.assunto_id
              FROM edital_assunto a JOIN edital_disciplina d ON d.id = a.disciplina_id
             WHERE d.analise_id = ? ORDER BY d.ordem, a.ordem, a.id""",
            rs -> {
                var x = new Ass(rs.getLong("id"), rs.getLong("disciplina_id"), rs.getString("chave"), rs.getInt("ordem"),
                    rs.getString("texto"), rs.getString("erros"), rs.getString("alertas"), rs.getBoolean("aceito"),
                    rs.getBoolean("aparece"), (Long) rs.getObject("assunto_id"));
                d.assuntos.add(x);
                d.assuntoPorId.put(x.id(), x);
            }, analiseId);

        var nos = new HashMap<Long, List<Long>>();
        var propostos = new HashMap<Long, List<Long>>();
        consultar(jdbc, """
            SELECT n.parte_id, n.proposto, n.no_id
              FROM edital_parte_no n
              JOIN edital_parte p ON p.id = n.parte_id
              JOIN edital_assunto a ON a.id = p.assunto_edital_id
              JOIN edital_disciplina d ON d.id = a.disciplina_id
             WHERE d.analise_id = ? ORDER BY n.parte_id, n.proposto, n.ordem""",
            rs -> {
                var mapa = rs.getBoolean("proposto") ? propostos : nos;
                mapa.computeIfAbsent(rs.getLong("parte_id"), k -> new ArrayList<>()).add(rs.getLong("no_id"));
            }, analiseId);
        consultar(jdbc, """
            SELECT p.id, p.assunto_edital_id, p.chave, p.ordem, p.texto, p.contexto, p.herdado, p.natureza, p.origem, p.estado,
                   p.arvore_versao_id, p.aparece, p.no_mudou, p.comentario, p.prop_natureza, p.ia_mudou
              FROM edital_parte p
              JOIN edital_assunto a ON a.id = p.assunto_edital_id
              JOIN edital_disciplina dd ON dd.id = a.disciplina_id
             WHERE dd.analise_id = ? ORDER BY dd.ordem, a.ordem, p.ordem, p.id""",
            rs -> d.partes.add(new Parte(rs.getLong("id"), rs.getLong("assunto_edital_id"), rs.getString("chave"),
                rs.getInt("ordem"), rs.getString("texto"), rs.getString("contexto"), rs.getString("herdado"),
                rs.getString("natureza"), rs.getString("origem"), rs.getString("estado"), rs.getLong("arvore_versao_id"),
                rs.getBoolean("aparece"), rs.getBoolean("no_mudou"), rs.getString("comentario"), rs.getString("prop_natureza"), rs.getBoolean("ia_mudou"),
                nos.getOrDefault(rs.getLong("id"), List.of()), propostos.getOrDefault(rs.getLong("id"), List.of()))), analiseId);
        consultar(jdbc, "SELECT id, disciplina_id, tipo, estado, motivo, linhas FROM edital_trecho WHERE analise_id = ? ORDER BY id",
            rs -> d.trechos.add(new Trecho(rs.getLong("id"), (Long) rs.getObject("disciplina_id"), rs.getString("tipo"),
                rs.getString("estado"), rs.getString("motivo"), rs.getString("linhas"))), analiseId);
        return d;
    }

    private static void consultar(JdbcTemplate jdbc, String sql, org.springframework.jdbc.core.RowCallbackHandler linha, Object... args) {
        jdbc.query(sql, linha, args);
    }

    List<Parte> partesDe(long assuntoEditalId) {
        return partes.stream().filter(p -> p.assuntoEditalId() == assuntoEditalId)
            .sorted(Comparator.comparingInt(Parte::ordem).thenComparingLong(Parte::id)).toList();
    }

    List<Ass> assuntosDe(long disciplinaId) {
        return assuntos.stream().filter(a -> a.disciplinaId() == disciplinaId).toList();
    }

    static List<String> lista(JsonMapper json, String conteudo) {
        var saida = new ArrayList<String>();
        if (conteudo == null || conteudo.isBlank()) return saida;
        json.readTree(conteudo).forEach(x -> saida.add(x.asString()));
        return saida;
    }
}
