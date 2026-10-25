package br.com.estudos.arvore;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import br.com.estudos.arvore.ArvoreResponses.Diferenca;
import br.com.estudos.arvore.ArvoreResponses.No;
import br.com.estudos.arvore.ArvoreResponses.Versao;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * A árvore de assuntos do QConcursos (01_DOMINIO §3.12, D-90; docs/SPRINT-25-CONFERENCIA-EDITAL.md §2.2, §4, §5).
 *
 * <p>O id de uma disciplina é guardado <b>negado</b>: no QConcursos os ids de disciplina e de assunto vêm de
 * espaços diferentes e colidem (50 casos na árvore de 2026-10-19). O arquivo continua com os ids originais; quem
 * o carrega aplica o sinal, e é assim que o planejamento entrega os nós.
 *
 * <p>Carregar uma versão nunca a torna vigente (exceto a primeira): quem decide quando atualizar é a pessoa (D-90).
 * JDBC em lote, sem entidade por nó (memória do Pi).
 */
@Service
public class ArvoreService {

    private static final int MAX_NOME = 500;

    private final JdbcTemplate jdbc;
    private final Map<Long, ArvoreSnapshot> cache = new ConcurrentHashMap<>();

    public ArvoreService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- carga

    /** O corpo é o formato de {@code taxonomia/qconcursos-AAAA-MM-DD.json}: fonte, data, quantidade_nos, disciplinas[]. */
    @Transactional
    public Versao carregar(JsonNode corpo) {
        var fonte = texto(corpo.get("fonte"));
        var data = data(texto(corpo.get("data")));
        if (fonte == null || data == null) {
            throw new ValidationException("A árvore precisa de fonte e data.", "ARVORE_INVALIDA", "fonte");
        }
        var rotulo = texto(corpo.get("rotulo")) != null ? texto(corpo.get("rotulo")) : fonte + "-" + data;

        var existente = jdbc.queryForList("SELECT id FROM arvore_versao WHERE rotulo = ?", Long.class, rotulo);
        if (!existente.isEmpty()) return versao(existente.get(0)); // idempotente

        var linhas = new ArrayList<ArvoreSnapshot.No>();
        var vistos = new HashSet<Long>();
        long ordemRaiz = 0;
        for (var disciplina : corpo.path("disciplinas")) {
            percorrer(disciplina, null, ++ordemRaiz, null, linhas, vistos);
        }
        if (linhas.isEmpty()) {
            throw new ValidationException("A árvore não tem nenhum nó.", "ARVORE_INVALIDA", "disciplinas");
        }

        var vigenteAnterior = jdbc.queryForList("SELECT id FROM arvore_versao WHERE vigente", Long.class);
        long id = jdbc.queryForObject(
            "INSERT INTO arvore_versao (rotulo, fonte, data, quantidade_nos, vigente) VALUES (?, ?, ?, ?, ?) RETURNING id",
            Long.class, rotulo, fonte, Date.valueOf(data), linhas.size(), vigenteAnterior.isEmpty());
        jdbc.batchUpdate(
            "INSERT INTO no_arvore (versao_id, no_id, nome, pai_id, ordem, raiz_id) VALUES (?, ?, ?, ?, ?, ?)",
            linhas, 1000, (ps, no) -> {
                ps.setLong(1, id);
                ps.setLong(2, no.id());
                ps.setString(3, no.nome());
                if (no.paiId() == null) ps.setNull(4, java.sql.Types.BIGINT); else ps.setLong(4, no.paiId());
                ps.setInt(5, no.ordem());
                ps.setLong(6, no.raizId());
            });
        if (!vigenteAnterior.isEmpty()) calcularDiferenca(id, vigenteAnterior.get(0));
        cache.remove(id);
        return versao(id);
    }

    private void percorrer(JsonNode no, Long paiId, long ordemPadrao, Long raizId,
                           List<ArvoreSnapshot.No> saida, HashSet<Long> vistos) {
        var original = no.path("id").asLong(0);
        var nome = texto(no.get("nome"));
        if (original <= 0 || nome == null) {
            throw new ValidationException("Nó sem id ou sem nome.", "ARVORE_INVALIDA", "disciplinas");
        }
        long id = paiId == null ? -original : original;
        if (!vistos.add(id)) {
            throw new ValidationException("Nó repetido na árvore: " + id + ".", "ARVORE_INVALIDA", "disciplinas");
        }
        long raiz = raizId == null ? id : raizId;
        int ordem = no.path("ordem").isNumber() ? no.get("ordem").asInt() : (int) ordemPadrao;
        var cortado = nome.length() <= MAX_NOME ? nome : nome.substring(0, MAX_NOME);
        saida.add(new ArvoreSnapshot.No(id, cortado, paiId, ordem, raiz, Textos.semAcento(cortado)));
        long irmao = 0;
        for (var filho : no.path("filhos")) {
            percorrer(filho, id, ++irmao, raiz, saida, vistos);
        }
    }

    /** Novos, removidos, renomeados e movidos da nova versão contra a vigente. */
    private void calcularDiferenca(long nova, long antiga) {
        jdbc.update("""
            INSERT INTO no_arvore_diferenca (versao_id, no_id, tipo, nome_anterior, pai_anterior_id)
            SELECT ?, n.no_id, 'NOVO', NULL, NULL
              FROM no_arvore n
             WHERE n.versao_id = ?
               AND NOT EXISTS (SELECT 1 FROM no_arvore a WHERE a.versao_id = ? AND a.no_id = n.no_id)
            UNION ALL
            SELECT ?, a.no_id, 'REMOVIDO', a.nome, a.pai_id
              FROM no_arvore a
             WHERE a.versao_id = ?
               AND NOT EXISTS (SELECT 1 FROM no_arvore n WHERE n.versao_id = ? AND n.no_id = a.no_id)
            UNION ALL
            SELECT ?, n.no_id, 'RENOMEADO', a.nome, NULL
              FROM no_arvore n JOIN no_arvore a ON a.versao_id = ? AND a.no_id = n.no_id
             WHERE n.versao_id = ? AND n.nome <> a.nome
            UNION ALL
            SELECT ?, n.no_id, 'MOVIDO', NULL, a.pai_id
              FROM no_arvore n JOIN no_arvore a ON a.versao_id = ? AND a.no_id = n.no_id
             WHERE n.versao_id = ? AND n.pai_id IS DISTINCT FROM a.pai_id
            """,
            nova, nova, antiga,
            nova, antiga, nova,
            nova, antiga, nova,
            nova, antiga, nova);
    }

    // ---------------------------------------------------------------- versões

    @Transactional(readOnly = true)
    public List<Versao> versoes() {
        return jdbc.queryForList("SELECT id FROM arvore_versao ORDER BY data DESC, id DESC", Long.class)
            .stream().map(this::versao).toList();
    }

    @Transactional(readOnly = true)
    public Versao versao(long id) {
        var linhas = jdbc.queryForList(
            "SELECT id, rotulo, fonte, data, quantidade_nos, vigente, carregada_em FROM arvore_versao WHERE id = ?", id);
        if (linhas.isEmpty()) throw new NotFoundException("Versão da árvore não encontrada.", "ARVORE_VERSAO_INEXISTENTE");
        var l = linhas.get(0);
        var contagem = new LinkedHashMap<String, Integer>();
        for (var tipo : List.of("NOVO", "REMOVIDO", "RENOMEADO", "MOVIDO")) contagem.put(tipo, 0);
        jdbc.query("SELECT tipo, count(*) AS n FROM no_arvore_diferenca WHERE versao_id = ? GROUP BY tipo",
            rs -> { contagem.put(rs.getString("tipo"), rs.getInt("n")); }, id);
        return new Versao(((Number) l.get("id")).longValue(), (String) l.get("rotulo"), (String) l.get("fonte"),
            ((Date) l.get("data")).toLocalDate(), ((Number) l.get("quantidade_nos")).intValue(), (Boolean) l.get("vigente"),
            ((java.sql.Timestamp) l.get("carregada_em")).toInstant(), contagem);
    }

    @Transactional(readOnly = true)
    public Long idDaVigente() {
        var ids = jdbc.queryForList("SELECT id FROM arvore_versao WHERE vigente", Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Transactional(readOnly = true)
    public Long idPorRotulo(String rotulo) {
        var ids = jdbc.queryForList("SELECT id FROM arvore_versao WHERE rotulo = ?", Long.class, rotulo);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Transactional(readOnly = true)
    public List<Diferenca> diferenca(long versaoId) {
        versao(versaoId);
        return jdbc.query("""
            SELECT d.tipo, d.no_id, COALESCE(n.nome, d.nome_anterior) AS nome, d.nome_anterior, d.pai_anterior_id, n.pai_id
              FROM no_arvore_diferenca d
              LEFT JOIN no_arvore n ON n.versao_id = d.versao_id AND n.no_id = d.no_id
             WHERE d.versao_id = ?
             ORDER BY d.tipo, d.no_id""",
            (rs, i) -> new Diferenca(rs.getString("tipo"), rs.getLong("no_id"), rs.getString("nome"),
                rs.getString("nome_anterior"),
                (Long) rs.getObject("pai_anterior_id"), (Long) rs.getObject("pai_id")), versaoId);
    }

    // ---------------------------------------------------------------- leitura

    /** A versão pedida, ou a vigente; sem nenhuma carregada, 404 ARVORE_NAO_CARREGADA. */
    private ArvoreSnapshot snapshot(Long versaoId) {
        var id = versaoId != null ? versaoId : idDaVigente();
        if (id == null) throw new NotFoundException("Nenhuma árvore do QConcursos foi carregada ainda.", "ARVORE_NAO_CARREGADA");
        return cache.computeIfAbsent(id, this::montar);
    }

    private ArvoreSnapshot montar(long id) {
        var rotulos = jdbc.queryForList("SELECT rotulo FROM arvore_versao WHERE id = ?", String.class, id);
        if (rotulos.isEmpty()) throw new NotFoundException("Versão da árvore não encontrada.", "ARVORE_VERSAO_INEXISTENTE");
        var nos = jdbc.query("SELECT no_id, nome, pai_id, ordem, raiz_id FROM no_arvore WHERE versao_id = ?",
            (rs, i) -> new ArvoreSnapshot.No(rs.getLong("no_id"), rs.getString("nome"), (Long) rs.getObject("pai_id"),
                rs.getInt("ordem"), rs.getLong("raiz_id"), Textos.semAcento(rs.getString("nome"))), id);
        return new ArvoreSnapshot(rotulos.get(0), nos);
    }

    @Transactional(readOnly = true)
    public No no(long noId, Long versaoId) {
        var s = snapshot(versaoId);
        var no = s.no(noId).orElseThrow(() -> new NotFoundException("Nó não existe nesta versão da árvore.", "NO_INEXISTENTE"));
        return resposta(s, no);
    }

    /** Vários nós de uma vez, na versão pedida; o que não existe nela fica de fora do mapa. */
    @Transactional(readOnly = true)
    public Map<Long, No> nosPorId(java.util.Collection<Long> ids, Long versaoId) {
        var s = snapshot(versaoId);
        var saida = new LinkedHashMap<Long, No>();
        for (var id : ids) s.no(id).ifPresent(n -> saida.put(id, resposta(s, n)));
        return saida;
    }

    /**
     * O que aconteceu com um nó entre duas versões: REMOVIDO (não existe na nova), MOVIDO (outro pai), RENOMEADO (outro
     * nome) ou nulo (igual). Só remoção e mudança de pai pedem que a pessoa reconfirme a ligação (D-90).
     */
    @Transactional(readOnly = true)
    public String tipoDeMudanca(long noId, long deVersao, long paraVersao) {
        if (deVersao == paraVersao) return null;
        var antes = snapshot(deVersao).no(noId);
        if (antes.isEmpty()) return null;
        var depois = snapshot(paraVersao).no(noId);
        if (depois.isEmpty()) return "REMOVIDO";
        if (!java.util.Objects.equals(antes.get().paiId(), depois.get().paiId())) return "MOVIDO";
        return antes.get().nome().equals(depois.get().nome()) ? null : "RENOMEADO";
    }

    /** Troca a versão vigente (a única que é): quem chama já mostrou o impacto à pessoa (D-90). */
    @Transactional
    public void tornarVigente(long versaoId) {
        versao(versaoId);
        jdbc.update("UPDATE arvore_versao SET vigente = false WHERE vigente AND id <> ?", versaoId);
        jdbc.update("UPDATE arvore_versao SET vigente = true WHERE id = ?", versaoId);
    }

    @Transactional(readOnly = true)
    public boolean existe(long noId, Long versaoId) {
        return snapshot(versaoId).no(noId).isPresent();
    }

    @Transactional(readOnly = true)
    public List<No> filhos(long noId, Long versaoId) {
        var s = snapshot(versaoId);
        s.no(noId).orElseThrow(() -> new NotFoundException("Nó não existe nesta versão da árvore.", "NO_INEXISTENTE"));
        return s.filhosDe(noId).stream().map(n -> resposta(s, n)).toList();
    }

    @Transactional(readOnly = true)
    public List<No> raizes(Long versaoId) {
        var s = snapshot(versaoId);
        return s.raizes().stream().map(n -> resposta(s, n)).toList();
    }

    @Transactional(readOnly = true)
    public List<No> buscar(String consulta, int limite, Long versaoId) {
        var s = snapshot(versaoId);
        return s.buscar(consulta, Math.max(1, Math.min(limite, 100))).stream().map(n -> resposta(s, n)).toList();
    }

    private No resposta(ArvoreSnapshot s, ArvoreSnapshot.No no) {
        return new No(no.id(), no.nome(), s.caminho(no), s.filhosDe(no.id()).size(), no.raizId(), s.rotulo());
    }

    // ---------------------------------------------------------------- apoio

    private static String texto(JsonNode no) {
        if (no == null || no.isNull() || no.isMissingNode()) return null;
        var valor = no.asString().trim();
        return valor.isEmpty() ? null : valor;
    }

    private static LocalDate data(String texto) {
        if (texto == null) return null;
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
