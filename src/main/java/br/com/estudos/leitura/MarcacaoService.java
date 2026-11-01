package br.com.estudos.leitura;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

/**
 * As marcações do leitor (01_DOMINIO §3.17, D-115 a D-118; docs/SPRINT-31-LEITOR.md §2 e §3).
 *
 * Cada gesto no aparelho é uma {@link Mudanca} com identificador próprio (D-118): a primeira cria a marcação (com a
 * forma), as seguintes acrescentam versões (cor, nota, removida). Nada se apaga (D-18) e a versão vigente é derivada
 * (D-117): a de maior hora de edição no aparelho, empate pela que chegou depois.
 */
@Service
public class MarcacaoService {

    public enum Tipo { DESTAQUE, NOTA_TRECHO, NOTA_PAGINA }

    /** Um gesto do aparelho. A forma (tipo, retângulos…) só vem na mudança que cria a marcação. */
    public record Mudanca(UUID mudancaId, UUID marcacaoChave, Long segmentoId, Integer pagina, Tipo tipo, UUID destaqueChave,
                          List<List<Double>> retangulos, String citacao, Double posicaoX, Double posicaoY,
                          String cor, String texto, boolean removida, Instant editadaEm) {}

    public record Recusa(UUID mudancaId, String codigo) {}

    public record Resultado(List<UUID> aplicadas, List<UUID> repetidas, List<Recusa> recusadas) {}

    /** A marcação como o leitor a desenha: a forma e a versão vigente. */
    public record Marcacao(UUID chave, long segmentoId, int pagina, Tipo tipo, UUID destaqueChave, List<List<Double>> retangulos,
                           String citacao, Double posicaoX, Double posicaoY, String cor, String texto, boolean removida,
                           OffsetDateTime editadaEm) {}

    public record Versao(UUID chave, Tipo tipo, int pagina, String citacao, String cor, String texto, boolean removida,
                         OffsetDateTime editadaEm, boolean vigente) {}

    public record Historico(List<Marcacao> removidas, List<Versao> versoes) {}

    /** A mudança se refere a uma marcação que ainda não chegou: fica na fila do aparelho até a criação chegar. */
    static final String DESCONHECIDA = "MARCACAO_DESCONHECIDA";
    /** A mudança nunca vai valer (forma ou cor fora da regra, bloco inexistente). */
    static final String INVALIDA = "MUDANCA_INVALIDA";

    private static final int LOTE_MAXIMO = 500;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate cadaUma;
    private final JsonMapper json;
    private final Clock relogio;

    public MarcacaoService(JdbcTemplate jdbc, PlatformTransactionManager transacoes, JsonMapper json, Clock relogio) {
        this.jdbc = jdbc;
        this.cadaUma = new TransactionTemplate(transacoes);
        this.cadaUma.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = json;
        this.relogio = relogio;
    }

    /** Aplica um lote, cada mudança na sua transação: uma recusada não derruba as outras. */
    public Resultado aplicar(List<Mudanca> lote) {
        var aplicadas = new ArrayList<UUID>();
        var repetidas = new ArrayList<UUID>();
        var recusadas = new ArrayList<Recusa>();
        if (lote == null) {
            return new Resultado(aplicadas, repetidas, recusadas);
        }
        for (var m : lote.subList(0, Math.min(lote.size(), LOTE_MAXIMO))) {
            if (m == null || m.mudancaId() == null || m.marcacaoChave() == null || m.editadaEm() == null) {
                recusadas.add(new Recusa(m == null ? null : m.mudancaId(), INVALIDA));
                continue;
            }
            String desfecho;
            try {
                desfecho = cadaUma.execute(s -> aplicarUma(m));
            } catch (DataIntegrityViolationException e) {
                desfecho = INVALIDA; // D-115/D-116 no banco: a forma ou a cor fora da regra
            }
            switch (desfecho) {
                case "APLICADA" -> aplicadas.add(m.mudancaId());
                case "REPETIDA" -> repetidas.add(m.mudancaId());
                default -> recusadas.add(new Recusa(m.mudancaId(), desfecho));
            }
        }
        return new Resultado(aplicadas, repetidas, recusadas);
    }

    private String aplicarUma(Mudanca m) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM marcacao_versao WHERE mudanca_id = ?)", Boolean.class, m.mudancaId()))) {
            return "REPETIDA";
        }
        var id = idDa(m.marcacaoChave());
        if (id == null) {
            if (m.tipo() == null) {
                return DESCONHECIDA;
            }
            Long destaqueId = null;
            if (m.tipo() == Tipo.NOTA_TRECHO) {
                destaqueId = m.destaqueChave() == null ? null : idDa(m.destaqueChave());
                if (destaqueId == null) {
                    return DESCONHECIDA;
                }
            }
            jdbc.update("""
                INSERT INTO marcacao (chave, segmento_id, pagina, tipo, destaque_id, retangulos, citacao, posicao_x, posicao_y, criada_em)
                VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?) ON CONFLICT (chave) DO NOTHING""",
                m.marcacaoChave(), m.segmentoId(), m.pagina(), m.tipo().name(), destaqueId,
                m.retangulos() == null ? null : json.writeValueAsString(m.retangulos()), m.citacao(), m.posicaoX(), m.posicaoY(), agora());
            id = idDa(m.marcacaoChave());
        }
        var gravadas = jdbc.update("""
            INSERT INTO marcacao_versao (marcacao_id, mudanca_id, cor, texto, removida, editada_em, recebida_em)
            VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (mudanca_id) DO NOTHING""",
            id, m.mudancaId(), m.cor(), vazioViraNulo(m.texto()), m.removida(), OffsetDateTime.ofInstant(m.editadaEm(), ZoneOffset.UTC), agora());
        return gravadas == 1 ? "APLICADA" : "REPETIDA";
    }

    /** As marcações vigentes de um bloco, sem as removidas nem as notas de destaque removido. */
    public List<Marcacao> vigentes(long segmentoId) {
        var todas = vigentesComRemovidas(segmentoId);
        var removidos = new HashSet<UUID>();
        todas.stream().filter(Marcacao::removida).forEach(x -> removidos.add(x.chave()));
        return todas.stream().filter(x -> !x.removida() && (x.destaqueChave() == null || !removidos.contains(x.destaqueChave()))).toList();
    }

    public Historico historico(long segmentoId) {
        var removidas = vigentesComRemovidas(segmentoId).stream().filter(Marcacao::removida).toList();
        var versoes = jdbc.query("""
            SELECT m.chave, m.tipo, m.pagina, m.citacao, v.cor, v.texto, v.removida, v.editada_em,
                   v.id = (SELECT v2.id FROM marcacao_versao v2 WHERE v2.marcacao_id = m.id ORDER BY v2.editada_em DESC, v2.id DESC LIMIT 1) AS vigente
            FROM marcacao m JOIN marcacao_versao v ON v.marcacao_id = m.id
            WHERE m.segmento_id = ? ORDER BY m.pagina, m.id, v.editada_em DESC, v.id DESC""",
            (rs, n) -> new Versao(rs.getObject("chave", UUID.class), Tipo.valueOf(rs.getString("tipo")), rs.getInt("pagina"), rs.getString("citacao"),
                rs.getString("cor"), rs.getString("texto"), rs.getBoolean("removida"), rs.getObject("editada_em", OffsetDateTime.class),
                rs.getBoolean("vigente")),
            segmentoId);
        return new Historico(removidas, versoes);
    }

    private List<Marcacao> vigentesComRemovidas(long segmentoId) {
        return jdbc.query("""
            SELECT DISTINCT ON (m.id) m.chave, m.segmento_id, m.pagina, m.tipo, d.chave AS destaque_chave, m.retangulos::text AS retangulos,
                   m.citacao, m.posicao_x, m.posicao_y, v.cor, v.texto, v.removida, v.editada_em
            FROM marcacao m
            JOIN marcacao_versao v ON v.marcacao_id = m.id
            LEFT JOIN marcacao d ON d.id = m.destaque_id
            WHERE m.segmento_id = ?
            ORDER BY m.id, v.editada_em DESC, v.id DESC""",
            (rs, n) -> new Marcacao(rs.getObject("chave", UUID.class), rs.getLong("segmento_id"), rs.getInt("pagina"), Tipo.valueOf(rs.getString("tipo")),
                rs.getObject("destaque_chave", UUID.class), retangulos(rs.getString("retangulos")), rs.getString("citacao"),
                rs.getObject("posicao_x") == null ? null : rs.getDouble("posicao_x"), rs.getObject("posicao_y") == null ? null : rs.getDouble("posicao_y"),
                rs.getString("cor"), rs.getString("texto"), rs.getBoolean("removida"), rs.getObject("editada_em", OffsetDateTime.class)),
            segmentoId);
    }

    @SuppressWarnings("unchecked")
    private List<List<Double>> retangulos(String texto) {
        if (texto == null) {
            return null;
        }
        var lista = json.readValue(texto, List.class);
        var saida = new ArrayList<List<Double>>();
        for (var r : (List<List<Number>>) lista) {
            saida.add(r.stream().map(Number::doubleValue).toList());
        }
        return saida;
    }

    private Long idDa(UUID chave) {
        var ids = jdbc.queryForList("SELECT id FROM marcacao WHERE chave = ?", Long.class, chave);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
    }

    private static String vazioViraNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip();
    }
}
