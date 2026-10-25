package br.com.estudos.conferencia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.arvore.ArvoreService;
import br.com.estudos.concurso.ConcursoService;
import br.com.estudos.conferencia.EditalInterno.DadosEdital;
import br.com.estudos.conferencia.EditalInterno.Decisao;
import br.com.estudos.conferencia.EditalInterno.Pendente;
import br.com.estudos.conferencia.EditalInterno.ResumoEntrega;
import br.com.estudos.edital.AnaliseEdital;
import br.com.estudos.edital.AnaliseEditalRepository;
import br.com.estudos.shared.enums.StatusAnaliseEdital;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Receber a leitura do edital que o planejamento entrega (contrato {@code edital-revisao/1}; 01_DOMINIO §3.12;
 * docs/SPRINT-25-CONFERENCIA-EDITAL.md §4). A entrega é idempotente pelo hash do corpo (D-89) e o texto do assunto
 * é guardado como veio (D-88).
 */
@Service
public class EntregaEditalService {

    static final String CONTRATO = "edital-revisao/1";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final AnaliseEditalRepository analises;
    private final ArvoreService arvore;
    private final ConcursoService concursos;

    public EntregaEditalService(JdbcTemplate jdbc, JsonMapper json, AnaliseEditalRepository analises,
                                ArvoreService arvore, ConcursoService concursos) {
        this.jdbc = jdbc;
        this.json = json;
        this.analises = analises;
        this.arvore = arvore;
        this.concursos = concursos;
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public List<Pendente> pendentes() {
        return analises.findByStatusIn(Set.of(StatusAnaliseEdital.SEPARANDO_ASSUNTOS)).stream()
            .map(a -> new Pendente(a.getId(), slugSugerido(a), a.getCargoEscolhido(), a.getNomeArquivo()))
            .toList();
    }

    @Transactional(readOnly = true)
    public DadosEdital dados(Long id) {
        var analise = buscar(id);
        if (analise.getStatus() != StatusAnaliseEdital.SEPARANDO_ASSUNTOS) {
            throw new BusinessRuleException("A análise não está esperando o planejamento (está em " + analise.getStatus() + ").", "ETAPA_INVALIDA");
        }
        var vigente = arvore.idDaVigente();
        var rotulo = vigente == null ? null : arvore.versao(vigente).rotulo();
        return new DadosEdital(analise.getId(), slugSugerido(analise), analise.getCargoEscolhido(), analise.getConcursoNome(),
            analise.getBanca(), analise.getPdfArquivo(), rotulo, decisoesDe(analise));
    }

    /** Só as ativas do escopo EDITAL desta análise; as de escopo TODOS entram na Sprint 26 (D-87). */
    private List<Decisao> decisoesDe(AnaliseEdital analise) {
        return jdbc.query("""
            SELECT d.parte_chave_norm, d.contexto_norm, d.nos, d.natureza, d.escopo, d.criada_em, v.rotulo
              FROM edital_decisao d JOIN arvore_versao v ON v.id = d.arvore_versao_id
             WHERE d.ativa AND d.escopo = 'EDITAL' AND d.analise_id = ?
             ORDER BY d.id""",
            (rs, i) -> new Decisao(rs.getString("parte_chave_norm"), vazioParaNulo(rs.getString("contexto_norm")),
                ids(rs.getString("nos")), minusculo(rs.getString("natureza")), rs.getString("escopo"),
                "analise-" + analise.getId(), rs.getString("rotulo"), rs.getTimestamp("criada_em").toInstant().toString(),
                "decisão da pessoa"), analise.getId());
    }

    private String slugSugerido(AnaliseEdital a) {
        return concursos.novoSlug(a.getConcursoNome() != null ? a.getConcursoNome() : "concurso");
    }

    // ---------------------------------------------------------------- entrega

    @Transactional
    public ResumoEntrega entregar(Long analiseId, String corpo) {
        var raiz = ler(corpo);
        if (!CONTRATO.equals(texto(raiz.get("contrato")))) {
            throw new ValidationException("O SGE só conhece " + CONTRATO + ".", "CONTRATO_DESCONHECIDO", "contrato");
        }
        var analise = buscar(analiseId);
        var hash = sha256(corpo);
        var existente = jdbc.queryForList("SELECT id FROM edital_execucao WHERE analise_id = ? AND hash = ?", Long.class, analiseId, hash);
        if (!existente.isEmpty()) return resumo(analiseId, existente.get(0), true);
        if (analise.getStatus() == StatusAnaliseEdital.EM_CONFERENCIA) {
            throw new BusinessRuleException("Reprocessar uma conferência já aberta entra na Sprint 26.", "REPROCESSAR_NA_26");
        }
        if (analise.getStatus() != StatusAnaliseEdital.SEPARANDO_ASSUNTOS) {
            throw new BusinessRuleException("A análise não está esperando o planejamento (está em " + analise.getStatus() + ").", "ETAPA_INVALIDA");
        }

        var execucao = raiz.path("execucao");
        var rotulo = texto(execucao.get("arvore"));
        var versaoId = rotulo == null ? null : arvore.idPorRotulo(rotulo);
        if (versaoId == null) {
            throw new BusinessRuleException("A árvore " + rotulo + " não foi carregada; carregue-a antes (POST /internal/arvores).", "ARVORE_NAO_CARREGADA");
        }
        validarNos(raiz, versaoId);

        long execucaoId = jdbc.queryForObject("""
            INSERT INTO edital_execucao (analise_id, modelo, prompt_versao, arvore_versao_id, executada_em, hash)
            VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, analiseId, cortar(texto(execucao.get("modelo")), 150),
            cortar(texto(execucao.get("prompt")), 50), versaoId, data(texto(execucao.get("data"))), hash);

        var nomes = new HashSet<String>();
        long ordemDisciplina = 0;
        for (var d : raiz.path("disciplinas")) {
            var nome = texto(d.get("nome"));
            if (nome == null || !nomes.add(nome)) {
                throw new ValidationException("Disciplina sem nome ou repetida: " + nome + ".", "ENTREGA_INVALIDA", "disciplinas");
            }
            long disciplinaId = jdbc.queryForObject("""
                INSERT INTO edital_disciplina (analise_id, ordem, nome, restricao, comum, questoes, qconcursos_ids, tentativas)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, analiseId, ++ordemDisciplina, cortar(nome, 250),
                cortar(texto(d.get("restricao")), 250), d.path("comum").asBoolean(false),
                d.path("questoes").isNumber() ? d.get("questoes").asInt() : null,
                json.writeValueAsString(d.path("qconcursos").isArray() ? d.get("qconcursos") : json.createArrayNode()),
                d.path("tentativas").isNumber() ? d.get("tentativas").asInt() : 1);
            for (var a : d.path("assuntos")) inserirAssunto(disciplinaId, a, versaoId, execucaoId);
            for (var t : d.path("trechos_sem_assunto")) inserirTrecho(analiseId, disciplinaId, "SEM_ASSUNTO", t);
        }
        for (var t : raiz.path("linhas_fora")) inserirTrecho(analiseId, null, "LINHA_FORA", t);

        analise.setStatus(StatusAnaliseEdital.EM_CONFERENCIA);
        analise.setEtapaFalha(null);
        analise.setErro(null);
        analises.save(analise);
        return resumo(analiseId, execucaoId, false);
    }

    private void inserirAssunto(long disciplinaId, JsonNode a, long versaoId, long execucaoId) {
        var chave = texto(a.get("chave"));
        var textoAssunto = texto(a.get("texto"));
        if (chave == null || textoAssunto == null) {
            throw new ValidationException("Assunto sem chave ou sem texto.", "ENTREGA_INVALIDA", "assuntos");
        }
        long assuntoId = jdbc.queryForObject("""
            INSERT INTO edital_assunto (disciplina_id, chave, ordem, texto, erros, alertas)
            VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, disciplinaId, chave, a.path("ordem").asInt(0), textoAssunto,
            lista(a.get("erros")), lista(a.get("alertas")));
        for (var p : a.path("partes")) {
            var chaveParte = texto(p.get("chave"));
            var textoParte = texto(p.get("texto"));
            if (chaveParte == null || textoParte == null) {
                throw new ValidationException("Parte sem chave ou sem texto.", "ENTREGA_INVALIDA", "partes");
            }
            var ligacao = p.path("ligacao");
            var nos = nosDe(ligacao);
            var natureza = nos.isEmpty() ? null : natureza(texto(ligacao.get("natureza")));
            var origem = origem(texto(ligacao.get("origem")));
            long parteId = jdbc.queryForObject("""
                INSERT INTO edital_parte (assunto_edital_id, chave, ordem, texto, contexto, herdado, natureza, origem, estado,
                                          arvore_versao_id, prop_natureza, prop_origem, execucao_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, assuntoId, chaveParte,
                p.path("ordem").asInt(0), textoParte, texto(p.get("contexto")), texto(p.get("herdado")), natureza, origem,
                "DECISAO".equals(origem) ? "DECIDIDA" : "PROPOSTA", versaoId, natureza, origem, execucaoId);
            for (int i = 0; i < nos.size(); i++) {
                jdbc.update("INSERT INTO edital_parte_no (parte_id, proposto, ordem, no_id) VALUES (?, false, ?, ?)", parteId, i + 1, nos.get(i));
            }
        }
    }

    private void inserirTrecho(long analiseId, Long disciplinaId, String tipo, JsonNode t) {
        jdbc.update("INSERT INTO edital_trecho (analise_id, disciplina_id, tipo, paginas, linhas) VALUES (?, ?, ?, ?, ?)",
            analiseId, disciplinaId, tipo, lista(t.isObject() ? t.get("paginas") : null),
            t.isObject() ? lista(t.get("linhas")) : json.writeValueAsString(List.of(t.asString())));
    }

    /** Todo nó da entrega precisa existir na versão da árvore da execução (docs §4: 422 NO_INEXISTENTE). */
    private void validarNos(JsonNode raiz, long versaoId) {
        var vistos = new HashSet<Long>();
        for (var d : raiz.path("disciplinas")) {
            for (var id : d.path("qconcursos")) conferir(id.asLong(), versaoId, vistos);
            for (var a : d.path("assuntos")) {
                for (var p : a.path("partes")) {
                    for (var no : nosDe(p.path("ligacao"))) conferir(no, versaoId, vistos);
                }
            }
        }
    }

    private void conferir(long no, long versaoId, Set<Long> vistos) {
        if (vistos.add(no) && !arvore.existe(no, versaoId)) {
            throw new ValidationException("O nó " + no + " não existe na árvore da execução.", "NO_INEXISTENTE", "nos");
        }
    }

    // ---------------------------------------------------------------- apoio

    private ResumoEntrega resumo(long analiseId, long execucaoId, boolean repetida) {
        int disciplinas = jdbc.queryForObject("SELECT count(*) FROM edital_disciplina WHERE analise_id = ?", Integer.class, analiseId);
        int assuntos = jdbc.queryForObject("SELECT count(*) FROM edital_assunto a JOIN edital_disciplina d ON d.id = a.disciplina_id WHERE d.analise_id = ?", Integer.class, analiseId);
        int partes = jdbc.queryForObject("""
            SELECT count(*) FROM edital_parte p JOIN edital_assunto a ON a.id = p.assunto_edital_id
              JOIN edital_disciplina d ON d.id = a.disciplina_id WHERE d.analise_id = ?""", Integer.class, analiseId);
        int semNo = jdbc.queryForObject("""
            SELECT count(*) FROM edital_parte p JOIN edital_assunto a ON a.id = p.assunto_edital_id
              JOIN edital_disciplina d ON d.id = a.disciplina_id
             WHERE d.analise_id = ? AND NOT EXISTS (SELECT 1 FROM edital_parte_no n WHERE n.parte_id = p.id AND NOT n.proposto)""",
            Integer.class, analiseId);
        int trechos = jdbc.queryForObject("SELECT count(*) FROM edital_trecho WHERE analise_id = ? AND estado = 'ABERTO'", Integer.class, analiseId);
        return new ResumoEntrega(execucaoId, repetida, disciplinas, assuntos, partes, semNo, trechos);
    }

    private AnaliseEdital buscar(Long id) {
        return analises.findById(id).orElseThrow(() -> new NotFoundException("Análise não encontrada.", "ANALISE_INEXISTENTE"));
    }

    private JsonNode ler(String corpo) {
        try {
            return json.readTree(corpo);
        } catch (RuntimeException e) {
            throw new ValidationException("O corpo não é um JSON válido.", "ENTREGA_INVALIDA", "corpo");
        }
    }

    private static List<Long> nosDe(JsonNode ligacao) {
        var saida = new ArrayList<Long>();
        for (var no : ligacao.path("nos")) saida.add(no.asLong());
        return saida;
    }

    private String lista(JsonNode no) {
        return json.writeValueAsString(no != null && no.isArray() ? no : json.createArrayNode());
    }

    private List<Long> ids(String conteudo) {
        var saida = new ArrayList<Long>();
        for (var no : ler(conteudo)) saida.add(no.asLong());
        return saida;
    }

    private static String natureza(String n) {
        if (n == null) return null;
        return switch (n) {
            case "exato" -> "EXATO";
            case "mais_amplo" -> "MAIS_AMPLO";
            case "parcial" -> "PARCIAL";
            default -> throw new ValidationException("Natureza desconhecida: " + n + ".", "ENTREGA_INVALIDA", "natureza");
        };
    }

    private static String origem(String o) {
        if (o == null) return "IA";
        return switch (o) {
            case "ia" -> "IA";
            case "refino" -> "REFINO";
            case "decisao" -> "DECISAO";
            default -> throw new ValidationException("Origem desconhecida: " + o + ".", "ENTREGA_INVALIDA", "origem");
        };
    }

    private static String minusculo(String s) {
        return s == null ? null : s.toLowerCase(java.util.Locale.ROOT);
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static String texto(JsonNode no) {
        if (no == null || no.isNull() || no.isMissingNode()) return null;
        var valor = no.asString();
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    private static String cortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static Date data(String texto) {
        if (texto == null) return null;
        try {
            return Date.valueOf(LocalDate.parse(texto));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String sha256(String corpo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(corpo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
