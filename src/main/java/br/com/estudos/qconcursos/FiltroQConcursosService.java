package br.com.estudos.qconcursos;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.qconcursos.LinkQConcursos.Aprendido;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * D-175 e D-176 (docs/SPRINT-49-FILTRO-QCONCURSOS.md): o filtro padrão do atalho do QConcursos, a exceção de cada concurso (só o que
 * difere; nulo segue o padrão) e os filtros aprendidos pelo endereço (cargo, áreas, instituição), com o nome que a pessoa deu.
 */
@Service
public class FiltroQConcursosService {

    /**
     * O filtro. No padrão, tudo preenchido; na exceção, nulo segue o padrão (em {@code aprendidos}, nulo segue os do padrão e lista
     * vazia tira todos).
     */
    public record Filtro(List<Integer> niveis, String banca, Integer anos, String formato, List<Integer> dificuldades, String ordem,
        Boolean tirarResolvidas, Boolean tirarAnuladas, Boolean tirarDesatualizadas, Boolean soComentadas, List<Aprendido> aprendidos) {}

    /** O filtro do concurso: a exceção (nula se não há) e o que vale, já com o padrão. */
    public record DoConcurso(long concursoId, Filtro excecao, Filtro efetivo) {}

    static final Set<String> BANCAS = Set.of("CONCURSO", "QUALQUER");
    static final Set<Integer> ANOS = Set.of(0, 3, 5, 10);
    static final Set<String> FORMATOS = Set.of("BANCA", "MULTIPLA_ESCOLHA", "CERTO_ERRADO", "QUALQUER");
    static final Set<String> ORDENS = Set.of("QC", "RECENTES", "DIFICEIS");

    private final JdbcTemplate jdbc;

    public FiltroQConcursosService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Filtro padrao() {
        return ler(idDoPadrao());
    }

    /** D-175: troca o padrão inteiro; nada pode faltar. */
    @Transactional
    public Filtro salvarPadrao(Filtro f) {
        if (f == null || f.niveis() == null || f.banca() == null || f.anos() == null || f.formato() == null || f.dificuldades() == null
            || f.ordem() == null || f.tirarResolvidas() == null || f.tirarAnuladas() == null || f.tirarDesatualizadas() == null
            || f.soComentadas() == null || f.aprendidos() == null) {
            throw new ValidationException("O filtro padrão precisa de todos os campos.", "FILTRO_INCOMPLETO", "filtro");
        }
        validar(f);
        var id = idDoPadrao();
        gravar(id, f);
        return ler(id);
    }

    @Transactional(readOnly = true)
    public DoConcurso doConcurso(long concursoId) {
        exigirConcurso(concursoId);
        var id = idDoConcurso(concursoId);
        var padrao = ler(idDoPadrao());
        var excecao = id == null ? null : ler(id);
        return new DoConcurso(concursoId, excecao, combinar(padrao, excecao));
    }

    /** D-175: a exceção guarda só o que difere; sem nada, volta ao padrão. */
    @Transactional
    public DoConcurso salvarExcecao(long concursoId, Filtro f) {
        exigirConcurso(concursoId);
        if (f == null || vazio(f)) {
            voltarAoPadrao(concursoId);
            return doConcurso(concursoId);
        }
        validar(f);
        var id = idDoConcurso(concursoId);
        if (id == null) {
            id = jdbc.queryForObject("INSERT INTO qconcursos_filtro (concurso_id) VALUES (?) RETURNING id", Long.class, concursoId);
        }
        gravar(id, f);
        return doConcurso(concursoId);
    }

    @Transactional
    public void voltarAoPadrao(long concursoId) {
        exigirConcurso(concursoId);
        jdbc.update("DELETE FROM qconcursos_filtro WHERE concurso_id = ?", concursoId);
    }

    /** D-175: o que vale para o concurso-alvo (o padrão, se não houver alvo ou exceção). */
    @Transactional(readOnly = true)
    public Filtro doAlvo() {
        var padrao = ler(idDoPadrao());
        var alvo = jdbc.queryForList("SELECT id FROM concurso WHERE ativo", Long.class);
        if (alvo.isEmpty()) return padrao;
        var id = idDoConcurso(alvo.get(0));
        return id == null ? padrao : combinar(padrao, ler(id));
    }

    /** D-176: os números de cargo, áreas e instituição do endereço, com o nome já guardado (ou nulo, para a pessoa dar). */
    @Transactional(readOnly = true)
    public List<Aprendido> lerEndereco(String endereco) {
        var achados = LinkQConcursos.aprendidos(endereco);
        if (achados.isEmpty()) {
            throw new ValidationException(
                "O endereço não tem cargo, área de formação, área de atuação nem instituição. Filtre no QConcursos e copie o endereço.",
                "ENDERECO_SEM_FILTRO", "endereco");
        }
        var nomes = rotulos();
        return achados.stream().map(a -> new Aprendido(a.tipo(), a.numero(), nomes.get(a.tipo() + ":" + a.numero()))).toList();
    }

    static Filtro combinar(Filtro p, Filtro e) {
        if (e == null) return p;
        return new Filtro(e.niveis() != null ? e.niveis() : p.niveis(), e.banca() != null ? e.banca() : p.banca(),
            e.anos() != null ? e.anos() : p.anos(), e.formato() != null ? e.formato() : p.formato(),
            e.dificuldades() != null ? e.dificuldades() : p.dificuldades(), e.ordem() != null ? e.ordem() : p.ordem(),
            e.tirarResolvidas() != null ? e.tirarResolvidas() : p.tirarResolvidas(),
            e.tirarAnuladas() != null ? e.tirarAnuladas() : p.tirarAnuladas(),
            e.tirarDesatualizadas() != null ? e.tirarDesatualizadas() : p.tirarDesatualizadas(),
            e.soComentadas() != null ? e.soComentadas() : p.soComentadas(), e.aprendidos() != null ? e.aprendidos() : p.aprendidos());
    }

    private static boolean vazio(Filtro f) {
        return f.niveis() == null && f.banca() == null && f.anos() == null && f.formato() == null && f.dificuldades() == null
            && f.ordem() == null && f.tirarResolvidas() == null && f.tirarAnuladas() == null && f.tirarDesatualizadas() == null
            && f.soComentadas() == null && f.aprendidos() == null;
    }

    private void validar(Filtro f) {
        if (f.niveis() != null && f.niveis().stream().anyMatch(n -> n == null || n < 1 || n > 3)) {
            throw new ValidationException("Nível: 1 (fundamental), 2 (médio) ou 3 (superior).", "FILTRO_INVALIDO", "niveis");
        }
        if (f.dificuldades() != null && f.dificuldades().stream().anyMatch(n -> n == null || n < 1 || n > 5)) {
            throw new ValidationException("Dificuldade: de 1 (muito fácil) a 5 (muito difícil).", "FILTRO_INVALIDO", "dificuldades");
        }
        if (f.banca() != null && !BANCAS.contains(f.banca())) {
            throw new ValidationException("Banca: CONCURSO ou QUALQUER.", "FILTRO_INVALIDO", "banca");
        }
        if (f.anos() != null && !ANOS.contains(f.anos())) {
            throw new ValidationException("Anos: 3, 5, 10 ou 0 (todos).", "FILTRO_INVALIDO", "anos");
        }
        if (f.formato() != null && !FORMATOS.contains(f.formato())) {
            throw new ValidationException("Formato: BANCA, MULTIPLA_ESCOLHA, CERTO_ERRADO ou QUALQUER.", "FILTRO_INVALIDO", "formato");
        }
        if (f.ordem() != null && !ORDENS.contains(f.ordem())) {
            throw new ValidationException("Ordem: QC, RECENTES ou DIFICEIS.", "FILTRO_INVALIDO", "ordem");
        }
        if (f.aprendidos() != null) {
            var nomes = rotulos();
            for (var a : f.aprendidos()) {
                if (a == null || !LinkQConcursos.PARAMETRO_APRENDIDO.containsKey(a.tipo()) || a.numero() <= 0) {
                    throw new ValidationException("Filtro aprendido: tipo CARGO, FORMACAO, ATUACAO ou INSTITUICAO e número positivo.",
                        "FILTRO_INVALIDO", "aprendidos");
                }
                var nome = a.nome() == null || a.nome().isBlank() ? nomes.get(a.tipo() + ":" + a.numero()) : a.nome().trim();
                if (nome == null) {
                    throw new ValidationException("Dê um nome ao número " + a.numero() + ".", "NOME_FALTANDO", "aprendidos");
                }
                if (nome.length() > 120) {
                    throw new ValidationException("Nome com até 120 caracteres.", "FILTRO_INVALIDO", "aprendidos");
                }
            }
        }
    }

    private void gravar(long id, Filtro f) {
        jdbc.update("""
            UPDATE qconcursos_filtro SET niveis = ?, banca = ?, anos = ?, formato = ?, dificuldades = ?, ordem = ?, tirar_resolvidas = ?,
                tirar_anuladas = ?, tirar_desatualizadas = ?, so_comentadas = ?, aprendidos_proprios = ?, atualizado_em = now()
            WHERE id = ?""",
            ps -> {
                ps.setObject(1, f.niveis() == null ? null : ps.getConnection().createArrayOf("smallint", distintos(f.niveis())));
                ps.setString(2, f.banca());
                ps.setObject(3, f.anos());
                ps.setString(4, f.formato());
                ps.setObject(5, f.dificuldades() == null ? null : ps.getConnection().createArrayOf("smallint", distintos(f.dificuldades())));
                ps.setString(6, f.ordem());
                ps.setObject(7, f.tirarResolvidas());
                ps.setObject(8, f.tirarAnuladas());
                ps.setObject(9, f.tirarDesatualizadas());
                ps.setObject(10, f.soComentadas());
                ps.setBoolean(11, f.aprendidos() != null);
                ps.setLong(12, id);
            });
        jdbc.update("DELETE FROM qconcursos_filtro_aprendido WHERE filtro_id = ?", id);
        if (f.aprendidos() == null) return;
        var vistos = new LinkedHashSet<String>();
        for (var a : f.aprendidos()) {
            if (!vistos.add(a.tipo() + ":" + a.numero())) continue;
            jdbc.update("INSERT INTO qconcursos_filtro_aprendido (filtro_id, tipo, numero) VALUES (?, ?, ?)", id, a.tipo(), a.numero());
            if (a.nome() != null && !a.nome().isBlank()) {
                // D-176: o nome dado pela pessoa vale nos próximos endereços.
                jdbc.update("""
                    INSERT INTO qconcursos_rotulo (tipo, numero, nome) VALUES (?, ?, ?)
                    ON CONFLICT (tipo, numero) DO UPDATE SET nome = EXCLUDED.nome, atualizado_em = now()""",
                    a.tipo(), a.numero(), a.nome().trim());
            }
        }
    }

    private Filtro ler(long id) {
        var nomes = rotulos();
        var aprendidos = new ArrayList<Aprendido>();
        jdbc.query("SELECT tipo, numero FROM qconcursos_filtro_aprendido WHERE filtro_id = ? ORDER BY tipo, numero",
            rs -> { aprendidos.add(new Aprendido(rs.getString(1), rs.getInt(2), nomes.get(rs.getString(1) + ":" + rs.getInt(2)))); }, id);
        return jdbc.queryForObject("""
            SELECT niveis, banca, anos, formato, dificuldades, ordem, tirar_resolvidas, tirar_anuladas, tirar_desatualizadas,
                so_comentadas, aprendidos_proprios
            FROM qconcursos_filtro WHERE id = ?""",
            (rs, n) -> new Filtro(inteiros(rs.getArray(1)), rs.getString(2), (Integer) rs.getObject(3, Integer.class), rs.getString(4),
                inteiros(rs.getArray(5)), rs.getString(6), booleano(rs, 7), booleano(rs, 8), booleano(rs, 9), booleano(rs, 10),
                rs.getBoolean(11) ? List.copyOf(aprendidos) : null),
            id);
    }

    private Map<String, String> rotulos() {
        var nomes = new HashMap<String, String>();
        jdbc.query("SELECT tipo, numero, nome FROM qconcursos_rotulo", rs -> { nomes.put(rs.getString(1) + ":" + rs.getInt(2), rs.getString(3)); });
        return nomes;
    }

    private long idDoPadrao() {
        return jdbc.queryForObject("SELECT id FROM qconcursos_filtro WHERE concurso_id IS NULL", Long.class);
    }

    private Long idDoConcurso(long concursoId) {
        return jdbc.queryForList("SELECT id FROM qconcursos_filtro WHERE concurso_id = ?", Long.class, concursoId).stream().findFirst()
            .orElse(null);
    }

    private void exigirConcurso(long concursoId) {
        if (jdbc.queryForObject("SELECT count(*) FROM concurso WHERE id = ?", Long.class, concursoId) == 0) {
            throw new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE");
        }
    }

    private static Object[] distintos(List<Integer> valores) {
        return new java.util.TreeSet<>(valores).stream().map(Integer::shortValue).toArray();
    }

    private static List<Integer> inteiros(Array array) throws SQLException {
        if (array == null) return null;
        return Arrays.stream((Object[]) array.getArray()).map(o -> ((Number) o).intValue()).sorted().toList();
    }

    private static Boolean booleano(ResultSet rs, int coluna) throws SQLException {
        return (Boolean) rs.getObject(coluna, Boolean.class);
    }
}
