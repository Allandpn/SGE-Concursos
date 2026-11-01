package br.com.estudos.leitura;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/** O que o leitor precisa para abrir um bloco (J-10; docs/SPRINT-31-LEITOR.md §3), e a última página aberta (D-121). */
@Service
public class LeituraService {

    public record Bloco(long id, int ordem) {}

    public record Leitura(long segmentoId, long assuntoId, String assuntoNome, int posicao, int total, List<Bloco> blocos, String arquivo,
                          Integer paginaInicial, Integer paginaFinal, Integer tempoEstimadoMin, Integer ultimaPagina,
                          List<MarcacaoService.Marcacao> marcacoes) {}

    private final JdbcTemplate jdbc;
    private final MarcacaoService marcacoes;
    private final Clock relogio;

    public LeituraService(JdbcTemplate jdbc, MarcacaoService marcacoes, Clock relogio) {
        this.jdbc = jdbc;
        this.marcacoes = marcacoes;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Leitura abrir(long segmentoId) {
        var linhas = jdbc.query("""
            SELECT s.id, s.assunto_id, a.nome, s.arquivo, s.pagina_inicial, s.pagina_final, s.tempo_estimado_min, l.pagina AS ultima
            FROM segmento s JOIN assunto a ON a.id = s.assunto_id LEFT JOIN segmento_leitura l ON l.segmento_id = s.id
            WHERE s.id = ?""",
            (rs, n) -> new Object[] {rs.getLong("assunto_id"), rs.getString("nome"), rs.getString("arquivo"),
                rs.getObject("pagina_inicial", Integer.class), rs.getObject("pagina_final", Integer.class),
                rs.getObject("tempo_estimado_min", Integer.class), rs.getObject("ultima", Integer.class)},
            segmentoId);
        if (linhas.isEmpty()) {
            throw new NotFoundException("Bloco não encontrado.", "SEGMENTO_NAO_ENCONTRADO");
        }
        var l = linhas.get(0);
        long assuntoId = (Long) l[0];
        // Os blocos ativos do assunto, na ordem de leitura: "Bloco 2 de 3" e o anterior/próximo.
        var blocos = jdbc.query("SELECT id, ordem FROM segmento WHERE assunto_id = ? AND arquivado_em IS NULL ORDER BY ordem, id",
            (rs, n) -> new Bloco(rs.getLong("id"), rs.getInt("ordem")), assuntoId);
        int posicao = 0;
        for (int i = 0; i < blocos.size(); i++) {
            if (blocos.get(i).id() == segmentoId) {
                posicao = i + 1;
            }
        }
        return new Leitura(segmentoId, assuntoId, (String) l[1], posicao, blocos.size(), blocos, (String) l[2], (Integer) l[3], (Integer) l[4],
            (Integer) l[5], (Integer) l[6], marcacoes.vigentes(segmentoId));
    }

    /** D-121: guarda a última página aberta. Conveniência: nenhuma métrica lê isto. */
    @Transactional
    public void guardarPagina(long segmentoId, Integer pagina) {
        if (pagina == null || pagina < 1) {
            throw new ValidationException("Página inválida.", "PAGINA_INVALIDA", "pagina");
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM segmento WHERE id = ?)", Boolean.class, segmentoId))) {
            throw new NotFoundException("Bloco não encontrado.", "SEGMENTO_NAO_ENCONTRADO");
        }
        jdbc.update("""
            INSERT INTO segmento_leitura (segmento_id, pagina, atualizada_em) VALUES (?, ?, ?)
            ON CONFLICT (segmento_id) DO UPDATE SET pagina = EXCLUDED.pagina, atualizada_em = EXCLUDED.atualizada_em""",
            segmentoId, pagina, OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC));
    }
}
