package br.com.estudos.pergunta;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/** As perguntas na recuperação (01_DOMINIO §3.18, D-124 a D-128; docs/SPRINT-32-PERGUNTAS.md §3). */
@Service
public class PerguntaService {

    public record Pergunta(long id, String tipo, String enunciado) {}

    public record Ponto(long id, String texto, boolean principal, int pagina, long segmentoId, int bloco) {}

    public record Gabarito(long perguntaId, List<Ponto> pontos) {}

    /** O que a tela manda junto com a recuperação (D-126). */
    public record Conferencia(List<Long> perguntasIds, List<Long> pontosQueVieram) {}

    public record PontoFaltante(String texto, long segmentoId, int bloco, int pagina) {}

    public record EstadoDoBloco(long segmentoId, String situacao, int prontas, int ruins) {}

    private final JdbcTemplate jdbc;
    private final Clock relogio;
    private final Random sorteio;

    public PerguntaService(JdbcTemplate jdbc, Clock relogio) {
        this.jdbc = jdbc;
        this.relogio = relogio;
        this.sorteio = new Random();
    }

    // --- D-124: quais aparecem --------------------------------------------------------------------------------------

    private record Candidata(long id, String tipo, String enunciado, boolean marcada, int faixa, long ultimoUso) {}

    @Transactional(readOnly = true)
    public List<Pergunta> selecionar(long assuntoId) {
        var candidatas = jdbc.query("""
            WITH uso AS (
                SELECT sp.pergunta_id, max(sp.sessao_id) AS ultima FROM sessao_pergunta sp JOIN sessao s ON s.id = sp.sessao_id -- D-160
                GROUP BY sp.pergunta_id
            )
            SELECT p.id, p.tipo, p.enunciado, p.origem_marcada, u.ultima,
                   EXISTS (SELECT 1 FROM sessao_ponto x JOIN pergunta_ponto pp ON pp.id = x.ponto_id
                           WHERE x.sessao_id = u.ultima AND pp.pergunta_id = p.id AND pp.principal AND NOT x.veio) AS faltou
            FROM pergunta p
            JOIN segmento g ON g.id = p.segmento_id
            LEFT JOIN uso u ON u.pergunta_id = p.id
            WHERE g.assunto_id = ? AND g.arquivado_em IS NULL AND p.ruim_em IS NULL""",
            (rs, n) -> {
                Long ultima = rs.getObject("ultima", Long.class);
                int faixa = ultima == null ? 0 : rs.getBoolean("faltou") ? 1 : 2;
                return new Candidata(rs.getLong("id"), rs.getString("tipo"), rs.getString("enunciado"), rs.getBoolean("origem_marcada"),
                    faixa, ultima == null ? 0 : ultima);
            },
            assuntoId);
        if (candidatas.isEmpty()) {
            return List.of();
        }
        int blocosLidos = jdbc.queryForObject("""
            SELECT count(DISTINCT p.segmento_id) FROM pergunta p JOIN segmento g ON g.id = p.segmento_id
            WHERE g.assunto_id = ? AND g.arquivado_em IS NULL AND p.ruim_em IS NULL""", Integer.class, assuntoId);
        int quantas = Math.min(candidatas.size(), Math.min(5, 2 + blocosLidos));
        // Sorteio antes da ordenação estável: o empate dentro de cada faixa sai diferente a cada recuperação.
        var lista = new ArrayList<>(candidatas);
        Collections.shuffle(lista, sorteio);
        lista.sort((a, b) -> {
            if (a.faixa() != b.faixa()) return Integer.compare(a.faixa(), b.faixa());
            if (a.marcada() != b.marcada()) return a.marcada() ? -1 : 1;
            return a.faixa() == 2 ? Long.compare(a.ultimoUso(), b.ultimoUso()) : 0;
        });
        return lista.subList(0, quantas).stream().map(c -> new Pergunta(c.id(), c.tipo(), c.enunciado())).toList();
    }

    // --- D-125: o gabarito, só por esta rota --------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Gabarito> gabarito(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() > 10) {
            throw new ValidationException("No máximo 10 perguntas por vez.", "PERGUNTAS_DEMAIS", "ids");
        }
        var porPergunta = new LinkedHashMap<Long, List<Ponto>>();
        ids.forEach(id -> porPergunta.put(id, new ArrayList<>()));
        jdbc.query("""
            SELECT pp.id, pp.pergunta_id, pp.texto, pp.principal, pp.pagina, g.id AS segmento_id, g.ordem
            FROM pergunta_ponto pp JOIN pergunta p ON p.id = pp.pergunta_id JOIN segmento g ON g.id = p.segmento_id
            WHERE pp.pergunta_id = ANY (?) ORDER BY pp.pergunta_id, pp.ordem""",
            rs -> {
                porPergunta.get(rs.getLong("pergunta_id")).add(new Ponto(rs.getLong("id"), rs.getString("texto"), rs.getBoolean("principal"),
                    rs.getInt("pagina"), rs.getLong("segmento_id"), rs.getInt("ordem")));
            },
            (Object) ids.toArray(Long[]::new));
        return porPergunta.entrySet().stream().map(e -> new Gabarito(e.getKey(), e.getValue())).toList();
    }

    // --- D-127 ----------------------------------------------------------------------------------------------------------

    @Transactional
    public void marcarRuim(long perguntaId, boolean ruim) {
        var n = jdbc.update("UPDATE pergunta SET ruim_em = ? WHERE id = ?", ruim ? OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC) : null, perguntaId);
        if (n == 0) {
            throw new NotFoundException("Pergunta não encontrada.", "PERGUNTA_NAO_ENCONTRADA");
        }
    }

    // --- D-126: os pontos marcados, gravados com a sessão --------------------------------------------------------------

    /** Na transação da sessão. Só perguntas do assunto e não tiradas; os pontos de cada uma são todos gravados (veio ou não). */
    public void gravarConferencia(long sessaoId, long assuntoId, Conferencia conferencia) {
        if (conferencia == null || conferencia.perguntasIds() == null || conferencia.perguntasIds().isEmpty()) {
            return;
        }
        var validas = jdbc.queryForList("""
            SELECT p.id FROM pergunta p JOIN segmento g ON g.id = p.segmento_id
            WHERE p.id = ANY (?) AND g.assunto_id = ? AND p.ruim_em IS NULL""",
            Long.class, conferencia.perguntasIds().toArray(Long[]::new), assuntoId);
        var vieram = new HashSet<>(conferencia.pontosQueVieram() == null ? List.<Long>of() : conferencia.pontosQueVieram());
        for (var perguntaId : validas) {
            jdbc.update("INSERT INTO sessao_pergunta (sessao_id, pergunta_id) VALUES (?, ?) ON CONFLICT DO NOTHING", sessaoId, perguntaId);
            for (var pontoId : jdbc.queryForList("SELECT id FROM pergunta_ponto WHERE pergunta_id = ?", Long.class, perguntaId)) {
                jdbc.update("INSERT INTO sessao_ponto (sessao_id, ponto_id, veio) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    sessaoId, pontoId, vieram.contains(pontoId));
            }
        }
    }

    // --- D-128: o que faltou, derivado ----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<PontoFaltante> faltaram(long sessaoId) {
        return jdbc.query("""
            SELECT pp.texto, g.id AS segmento_id, g.ordem, pp.pagina
            FROM sessao_ponto x JOIN pergunta_ponto pp ON pp.id = x.ponto_id JOIN pergunta p ON p.id = pp.pergunta_id
            JOIN segmento g ON g.id = p.segmento_id
            WHERE x.sessao_id = ? AND NOT x.veio AND p.ruim_em IS NULL
            ORDER BY g.ordem, pp.pagina, pp.id""",
            (rs, n) -> new PontoFaltante(rs.getString("texto"), rs.getLong("segmento_id"), rs.getInt("ordem"), rs.getInt("pagina")),
            sessaoId);
    }

    // --- Estado por bloco (tela 8 do wireframe) -----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<EstadoDoBloco> estado(long assuntoId) {
        return jdbc.query("""
            SELECT g.id, coalesce(pr.situacao, 'NAO_LIDO') AS situacao,
                   (SELECT count(*) FROM pergunta p WHERE p.segmento_id = g.id AND p.ruim_em IS NULL) AS prontas,
                   (SELECT count(*) FROM pergunta p WHERE p.segmento_id = g.id AND p.ruim_em IS NOT NULL) AS ruins
            FROM segmento g LEFT JOIN pergunta_preparo pr ON pr.segmento_id = g.id
            WHERE g.assunto_id = ? AND g.arquivado_em IS NULL ORDER BY g.ordem, g.id""",
            (rs, n) -> new EstadoDoBloco(rs.getLong("id"), rs.getString("situacao"), rs.getInt("prontas"), rs.getInt("ruins")),
            assuntoId);
    }
}
