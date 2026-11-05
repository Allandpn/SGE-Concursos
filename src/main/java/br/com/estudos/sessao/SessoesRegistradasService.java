package br.com.estudos.sessao;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.revisao.RefazerEscada;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Sessões registradas (01_DOMINIO §3.26, D-160 a D-162; 04_FRONTEND §8D; ADR-062): a lista por dia, com as tiradas, e o gesto de
 * tirar da escada ou pôr de volta, que refaz a escada dos assuntos afetados. Lê e grava a tabela {@code sessao_registro}; o resto do
 * sistema lê a visão {@code sessao}, só das ativas.
 */
@Service
public class SessoesRegistradasService {

    public record Linha(long id, LocalDate data, long assuntoId, String assunto, String disciplina, String tipo, int tempoMinutos,
        String resultado, Integer questoesCorretas, Integer questoesTotal, boolean ativa) {}

    public record Pagina(LocalDate de, LocalDate ate, List<Linha> sessoes, boolean temAnteriores) {}

    public record Feito(int sessoes, List<String> assuntos) {}

    static final int DIAS_POR_PAGINA = 30;
    static final int MAXIMO_POR_GESTO = 200;

    private final JdbcTemplate jdbc;
    private final RefazerEscada refazerEscada;
    private final Clock clock;

    public SessoesRegistradasService(JdbcTemplate jdbc, RefazerEscada refazerEscada, Clock clock) {
        this.jdbc = jdbc;
        this.refazerEscada = refazerEscada;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Pagina listar(LocalDate ate) {
        var fim = ate == null ? LocalDate.now(clock) : ate;
        var inicio = fim.minusDays(DIAS_POR_PAGINA - 1L);
        var linhas = jdbc.query("""
            SELECT s.id, s.data, s.assunto_id, a.nome AS assunto, d.nome AS disciplina, s.tipo, s.tempo_minutos, s.resultado,
                   s.questoes_corretas, s.questoes_total, s.ativo
              FROM sessao_registro s JOIN assunto a ON a.id = s.assunto_id JOIN disciplina d ON d.id = a.disciplina_id
             WHERE s.data BETWEEN ? AND ?
             ORDER BY s.data DESC, s.id DESC""",
            (rs, n) -> new Linha(rs.getLong("id"), rs.getObject("data", LocalDate.class), rs.getLong("assunto_id"), rs.getString("assunto"),
                rs.getString("disciplina"), rs.getString("tipo"), rs.getInt("tempo_minutos"), rs.getString("resultado"),
                rs.getObject("questoes_corretas", Integer.class), rs.getObject("questoes_total", Integer.class), rs.getBoolean("ativo")),
            inicio, fim);
        var anteriores = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM sessao_registro WHERE data < ?)", Boolean.class, inicio);
        return new Pagina(inicio, fim, linhas, Boolean.TRUE.equals(anteriores));
    }

    /** D-160: tira as sessões da escada; D-161: refaz a escada de cada assunto afetado. */
    @Transactional
    public Feito tirar(List<Long> ids) {
        return mudar(ids, false);
    }

    /** D-162: põe de volta; D-161 de novo. */
    @Transactional
    public Feito porDeVolta(List<Long> ids) {
        return mudar(ids, true);
    }

    private Feito mudar(List<Long> ids, boolean ativa) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("Marque ao menos uma sessão.", "SESSOES_NENHUMA", "ids");
        }
        if (ids.size() > MAXIMO_POR_GESTO) {
            throw new ValidationException("No máximo " + MAXIMO_POR_GESTO + " sessões de uma vez.", "SESSOES_DEMAIS", "ids");
        }
        var vetor = ids.toArray(Long[]::new);
        var assuntos = jdbc.queryForList(
            "SELECT DISTINCT assunto_id FROM sessao_registro WHERE id = ANY (?) AND ativo <> ? ORDER BY assunto_id", Long.class, vetor, ativa);
        int n = jdbc.update("UPDATE sessao_registro SET ativo = ?, atualizado_em = now() WHERE id = ANY (?) AND ativo <> ?", ativa, vetor, ativa);
        refazerEscada.refazer(assuntos);
        var nomes = assuntos.isEmpty() ? List.<String>of() : jdbc.queryForList(
            "SELECT nome FROM assunto WHERE id = ANY (?) ORDER BY nome", String.class, (Object) assuntos.toArray(Long[]::new));
        return new Feito(n, nomes);
    }
}
