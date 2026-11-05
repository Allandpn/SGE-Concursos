package br.com.estudos.qconcursos;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.pesobanca.PesoBanca;
import br.com.estudos.regua.ReguaService;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/** O atalho para o QConcursos e o catálogo de bancas (01_DOMINIO §3.24, D-150 a D-152; ADR-058; docs/SPRINT-36-QCONCURSOS.md §2). */
@Service
public class QConcursosService {

    /** Por que o atalho é mais largo do que o assunto: só a disciplina, ou sem banca. */
    public static final String AVISO_DISCIPLINA = "DISCIPLINA";
    public static final String AVISO_SEM_BANCA = "SEM_BANCA";
    /** D-173: um nó ligado pela pessoa saiu da árvore vigente; o atalho usa os que ficaram, senão volta ao edital. */
    public static final String AVISO_NO_FORA_DA_ARVORE = "NO_FORA_DA_ARVORE";

    /** {@code vinculado}: o filtro veio do vínculo escolhido pela pessoa (D-173), não do edital. */
    public record Atalho(long assuntoId, String url, int sugeridas, String banca, String formato, List<String> filtro,
        List<String> avisos, boolean vinculado) {}

    /** A banca como está no concurso; o número e o nome curto no catálogo, se houver. */
    public record BancaDoConcurso(String banca, Integer numero, String origem, String nomeNoCatalogo) {}

    private record EditalDoAssunto(long editalAssuntoId, long editalDisciplinaId) {}

    private record No(long noId, Long raizId, String nome, String raizNome) {}

    private static final Pattern DIGITOS = Pattern.compile("-?\\d+");

    private final JdbcTemplate jdbc;
    private final ReguaService regua;
    private final Clock clock;

    public QConcursosService(JdbcTemplate jdbc, ReguaService regua, Clock clock) {
        this.jdbc = jdbc;
        this.regua = regua;
        this.clock = clock;
    }

    /** D-150 a D-152: o atalho de cada assunto que tem para onde ir; os que não têm ficam de fora. */
    @Transactional(readOnly = true)
    public List<Atalho> atalhos(Collection<Long> assuntoIds) {
        var ids = new LinkedHashSet<>(assuntoIds == null ? List.<Long>of() : assuntoIds);
        if (ids.isEmpty()) return List.of();
        var arrayIds = ids.toArray(Long[]::new);

        var disciplinaDoAssunto = new HashMap<Long, String>();
        jdbc.query("SELECT a.id, d.nome FROM assunto a JOIN disciplina d ON d.id = a.disciplina_id WHERE a.id = ANY (?)",
            rs -> { disciplinaDoAssunto.put(rs.getLong(1), rs.getString(2)); }, (Object) arrayIds);

        // A análise mais recente que tem o assunto.
        var edital = new HashMap<Long, EditalDoAssunto>();
        jdbc.query("""
            SELECT DISTINCT ON (assunto_id) assunto_id, id, disciplina_id FROM edital_assunto
            WHERE assunto_id = ANY (?) ORDER BY assunto_id, id DESC""",
            rs -> { edital.put(rs.getLong(1), new EditalDoAssunto(rs.getLong(2), rs.getLong(3))); }, (Object) arrayIds);

        // Os nós vigentes das partes aceitas ou decididas, com a raiz (a disciplina) de cada um.
        var nosDoEdital = new HashMap<Long, List<No>>();
        if (!edital.isEmpty()) {
            jdbc.query("""
                SELECT ep.assunto_edital_id, n.no_id, na.raiz_id, na.nome, r.nome
                FROM edital_parte ep
                JOIN edital_parte_no n ON n.parte_id = ep.id AND NOT n.proposto
                LEFT JOIN no_arvore na ON na.versao_id = ep.arvore_versao_id AND na.no_id = n.no_id
                LEFT JOIN no_arvore r ON r.versao_id = ep.arvore_versao_id AND r.no_id = na.raiz_id
                WHERE ep.assunto_edital_id = ANY (?) AND ep.estado IN ('ACEITA', 'DECIDIDA') AND ep.aparece
                ORDER BY ep.ordem, n.ordem""",
                rs -> {
                    nosDoEdital.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                        .add(new No(rs.getLong(2), (Long) rs.getObject(3, Long.class), rs.getString(4), rs.getString(5)));
                },
                (Object) edital.values().stream().map(EditalDoAssunto::editalAssuntoId).toArray(Long[]::new));
        }

        // D-173: o vínculo escolhido pela pessoa, com nome e raiz na árvore vigente (nó que saiu dela vem sem nome).
        var vinculos = new HashMap<Long, List<No>>();
        jdbc.query("""
            SELECT v.assunto_id, v.no_id, na.raiz_id, na.nome, r.nome, na.no_id IS NOT NULL
            FROM assunto_qconcursos_no v
            LEFT JOIN arvore_versao av ON av.vigente
            LEFT JOIN no_arvore na ON na.versao_id = av.id AND na.no_id = v.no_id
            LEFT JOIN no_arvore r ON r.versao_id = av.id AND r.no_id = na.raiz_id
            WHERE v.assunto_id = ANY (?) ORDER BY v.assunto_id, v.ordem""",
            rs -> {
                vinculos.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                    .add(rs.getBoolean(6) ? new No(rs.getLong(2), (Long) rs.getObject(3, Long.class), rs.getString(4), rs.getString(5)) : null);
            }, (Object) arrayIds);

        var raizes = raizesDaArvoreVigente();
        var banca = bancaDoAlvo();
        var foco = parametro("pomodoro_foco_min");
        var porQuestao = parametro("minutos_por_questao");
        var minimo = (int) parametro("lote_minimo_questoes");
        var porVez = (int) parametro("questoes_por_vez");
        // D-174: com "questões por vez", a página abre com esse número; 0 é o automático de D-152.
        var sugeridas = porVez > 0 ? porVez : LinkQConcursos.sugeridas(foco, porQuestao, minimo);
        var ano = LocalDate.now(clock).getYear();
        var anos = (int) parametro("banca_anos");

        var saida = new ArrayList<Atalho>();
        for (var assuntoId : ids) {
            var disciplinas = new LinkedHashSet<Long>();
            var assuntos = new LinkedHashSet<Long>();
            var filtro = new ArrayList<String>();
            var avisos = new ArrayList<String>();
            var doEdital = edital.get(assuntoId);
            var ligados = vinculos.getOrDefault(assuntoId, new ArrayList<>());
            if (ligados.stream().anyMatch(java.util.Objects::isNull)) avisos.add(AVISO_NO_FORA_DA_ARVORE);
            var vigentes = ligados.stream().filter(java.util.Objects::nonNull).toList();
            var vinculado = !vigentes.isEmpty();
            var nos = vinculado ? vigentes : doEdital == null ? List.<No>of() : nosDoEdital.getOrDefault(doEdital.editalAssuntoId(), List.of());
            for (var no : nos) {
                if (no.noId() < 0) {
                    disciplinas.add(-no.noId());
                    filtro.add(nomeOu(no.nome(), "disciplina " + -no.noId()));
                } else {
                    assuntos.add(no.noId());
                    if (no.raizId() != null) disciplinas.add(-no.raizId());
                    filtro.add(nomeOu(no.nome(), "assunto " + no.noId()) + (no.raizNome() != null ? " (" + no.raizNome() + ")" : ""));
                }
            }
            if (disciplinas.isEmpty() && assuntos.isEmpty()) {
                // Sem nó: a disciplina da análise, senão a da árvore com o mesmo nome (D-150).
                if (doEdital != null) disciplinas.addAll(disciplinasDaAnalise(doEdital.editalDisciplinaId()));
                var nomeSge = disciplinaDoAssunto.get(assuntoId);
                if (disciplinas.isEmpty() && nomeSge != null) {
                    var raiz = raizes.get(normalizar(nomeSge));
                    if (raiz != null) disciplinas.add(-raiz);
                }
                if (disciplinas.isEmpty()) continue;
                avisos.add(AVISO_DISCIPLINA);
                filtro.add("disciplina " + (nomeSge == null ? "" : nomeSge));
            }
            if (banca.numero() == null) avisos.add(AVISO_SEM_BANCA);
            var formato = regua.formatoSugerido(assuntoId);
            var url = LinkQConcursos.montar(new LinkQConcursos.Filtro(disciplinas, assuntos, banca.numero(), formato, ano - anos, ano,
                LinkQConcursos.porPagina(sugeridas)));
            saida.add(new Atalho(assuntoId, url, sugeridas, banca.numero() == null ? null : banca.nomeNoCatalogo(), formato, filtro, avisos,
                vinculado));
        }
        return saida;
    }

    /** D-151: a banca do concurso (senão a da prova hospedada mais recente dele) e o número no catálogo. */
    @Transactional(readOnly = true)
    public BancaDoConcurso bancaDoConcurso(long concursoId) {
        var nome = nomeDaBanca(concursoId);
        if (nome == null) return new BancaDoConcurso(null, null, null, null);
        return catalogo(nome).map(c -> new BancaDoConcurso(nome, c.numero(), c.origem(), c.nome()))
            .orElse(new BancaDoConcurso(nome, null, null, null));
    }

    /** D-151: aprende o número da banca do concurso pelo endereço colado; vale para toda banca com o mesmo nome. */
    @Transactional
    public BancaDoConcurso aprenderBanca(long concursoId, String endereco) {
        var nome = nomeDaBanca(concursoId);
        if (nome == null) {
            throw new BusinessRuleException("O concurso não tem banca. Informe a banca no concurso primeiro.", "SEM_BANCA");
        }
        var numero = LinkQConcursos.numeroDaBanca(endereco).orElseThrow(() -> new ValidationException(
            "Cole um endereço do QConcursos filtrado por uma única banca, ou o número dela.", "ENDERECO_SEM_BANCA", "endereco"));
        var chave = PesoBanca.normalizar(nome);
        if (chave.isEmpty()) {
            throw new BusinessRuleException("O nome da banca do concurso não tem letras nem números.", "SEM_BANCA");
        }
        jdbc.update("""
            INSERT INTO qconcursos_banca (nome_norm, nome, numero, origem) VALUES (?, ?, ?, 'ENDERECO')
            ON CONFLICT (nome_norm) DO UPDATE SET nome = EXCLUDED.nome, numero = EXCLUDED.numero, origem = 'ENDERECO', atualizado_em = now()""",
            chave, nome, numero);
        return bancaDoConcurso(concursoId);
    }

    private BancaDoConcurso bancaDoAlvo() {
        var alvo = jdbc.queryForList("SELECT id FROM concurso WHERE ativo", Long.class);
        return alvo.isEmpty() ? new BancaDoConcurso(null, null, null, null) : bancaDoConcurso(alvo.get(0));
    }

    private String nomeDaBanca(long concursoId) {
        var concurso = jdbc.query("SELECT banca FROM concurso WHERE id = ?", (rs, n) -> new String[] {rs.getString(1)}, concursoId);
        if (concurso.isEmpty()) {
            throw new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE");
        }
        var nome = concurso.get(0)[0];
        if (nome == null || PesoBanca.normalizar(nome).isEmpty()) {
            nome = jdbc.queryForList("SELECT banca FROM prova WHERE concurso_id = ? AND banca IS NOT NULL ORDER BY id DESC LIMIT 1",
                String.class, concursoId).stream().findFirst().orElse(null);
        }
        return nome == null || PesoBanca.normalizar(nome).isEmpty() ? null : nome;
    }

    private record Catalogado(int numero, String origem, String nome) {}

    /** O nome exato no catálogo; senão a chave mais longa (3+ letras) que começa o nome ("FGV - Fundação …" é FGV). */
    private Optional<Catalogado> catalogo(String nome) {
        var chave = PesoBanca.normalizar(nome);
        var linhas = new LinkedHashMap<String, Catalogado>();
        jdbc.query("SELECT nome_norm, numero, origem, nome FROM qconcursos_banca ORDER BY length(nome_norm) DESC",
            rs -> { linhas.put(rs.getString(1), new Catalogado(rs.getInt(2), rs.getString(3), rs.getString(4))); });
        if (linhas.containsKey(chave)) return Optional.of(linhas.get(chave));
        return linhas.entrySet().stream().filter(e -> e.getKey().length() >= 3 && chave.startsWith(e.getKey()))
            .map(Map.Entry::getValue).findFirst();
    }

    private List<Long> disciplinasDaAnalise(long editalDisciplinaId) {
        var texto = jdbc.queryForList("SELECT qconcursos_ids FROM edital_disciplina WHERE id = ?", String.class, editalDisciplinaId)
            .stream().findFirst().orElse("[]");
        var ids = new ArrayList<Long>();
        var m = DIGITOS.matcher(texto == null ? "" : texto);
        while (m.find()) {
            var v = Math.abs(Long.parseLong(m.group()));
            if (v > 0) ids.add(v);
        }
        return ids;
    }

    /** As disciplinas (raízes) da árvore vigente, pelo nome normalizado → id do nó (negativo). */
    private Map<String, Long> raizesDaArvoreVigente() {
        var raizes = new HashMap<String, Long>();
        jdbc.query("""
            SELECT n.no_id, n.nome FROM no_arvore n JOIN arvore_versao v ON v.id = n.versao_id
            WHERE v.vigente AND n.pai_id IS NULL ORDER BY n.no_id""",
            rs -> { raizes.putIfAbsent(normalizar(rs.getString(2)), rs.getLong(1)); });
        return raizes;
    }

    private double parametro(String chave) {
        return Double.parseDouble(jdbc.queryForObject("SELECT valor FROM parametro WHERE chave = ?", String.class, chave));
    }

    private static String nomeOu(String nome, String padrao) {
        return nome == null || nome.isBlank() ? padrao : nome;
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
