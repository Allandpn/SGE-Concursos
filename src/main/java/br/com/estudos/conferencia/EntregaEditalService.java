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
            .map(a -> new Pendente(a.getId(), slugSugerido(a), a.getCargoEscolhido(), a.getNomeArquivo(), a.isReprocessar()))
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
            SELECT d.parte_texto, d.contexto_texto, d.nos, d.natureza, d.escopo, d.criada_em, v.rotulo
              FROM edital_decisao d JOIN arvore_versao v ON v.id = d.arvore_versao_id
             WHERE d.ativa AND d.escopo = 'EDITAL' AND d.analise_id = ?
             ORDER BY d.id""",
            (rs, i) -> new Decisao(rs.getString("parte_texto"), vazioParaNulo(rs.getString("contexto_texto")),
                ids(rs.getString("nos")), minusculo(rs.getString("natureza")), rs.getString("escopo"),
                "analise-" + analise.getId(), rs.getString("rotulo"), rs.getTimestamp("criada_em").toInstant().toString(),
                "decisão da pessoa"), analise.getId());
    }

    private String slugSugerido(AnaliseEdital a) {
        return concursos.novoSlug(a.getConcursoNome() != null ? a.getConcursoNome() : "concurso");
    }

    // ---------------------------------------------------------------- entrega

    /** O que a fusão de uma entrega mudou (docs/SPRINT-26 §3). */
    private static final class Fusao {
        int partesNovas, partesQueSumiram, iaMudou;
    }

    @Transactional
    public ResumoEntrega entregar(Long analiseId, String corpo) {
        var raiz = ler(corpo);
        if (!CONTRATO.equals(texto(raiz.get("contrato")))) {
            throw new ValidationException("O SGE só conhece " + CONTRATO + ".", "CONTRATO_DESCONHECIDO", "contrato");
        }
        var analise = buscar(analiseId);
        var hash = sha256(corpo);
        var existente = jdbc.queryForList("SELECT id FROM edital_execucao WHERE analise_id = ? AND hash = ?", Long.class, analiseId, hash);
        if (!existente.isEmpty()) return resumo(analiseId, existente.get(0), true, new Fusao());
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

        // Há leitura anterior (reprocessar): a entrega se funde nela, pela chave (D-89), sem tocar o que a pessoa decidiu (D-86).
        boolean fundir = jdbc.queryForObject("SELECT count(*) FROM edital_disciplina WHERE analise_id = ?", Integer.class, analiseId) > 0;
        var fusao = new Fusao();
        if (fundir) jdbc.update("""
            UPDATE edital_parte SET ia_mudou = false WHERE assunto_edital_id IN (
                SELECT a.id FROM edital_assunto a JOIN edital_disciplina d ON d.id = a.disciplina_id WHERE d.analise_id = ?)""", analiseId);

        var nomes = new HashSet<String>();
        long ordemDisciplina = jdbc.queryForObject("SELECT COALESCE(max(ordem), 0) FROM edital_disciplina WHERE analise_id = ?", Long.class, analiseId);
        for (var d : raiz.path("disciplinas")) {
            var nome = texto(d.get("nome"));
            if (nome == null || !nomes.add(nome)) {
                throw new ValidationException("Disciplina sem nome ou repetida: " + nome + ".", "ENTREGA_INVALIDA", "disciplinas");
            }
            var qconcursos = json.writeValueAsString(d.path("qconcursos").isArray() ? d.get("qconcursos") : json.createArrayNode());
            var textoDisciplina = d.path("texto").isString() ? d.get("texto").asString() : null;
            var tentativas = d.path("tentativas").isNumber() ? d.get("tentativas").asInt() : 1;
            var questoes = d.path("questoes").isNumber() ? d.get("questoes").asInt() : null;
            var achada = fundir ? jdbc.queryForList("SELECT id FROM edital_disciplina WHERE analise_id = ? AND nome = ?", Long.class, analiseId, cortar(nome, 250)) : List.<Long>of();
            long disciplinaId;
            if (achada.isEmpty()) {
                disciplinaId = jdbc.queryForObject("""
                    INSERT INTO edital_disciplina (analise_id, ordem, nome, restricao, comum, questoes, qconcursos_ids, texto, referencias, tentativas)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, analiseId, ++ordemDisciplina, cortar(nome, 250),
                    cortar(texto(d.get("restricao")), 250), d.path("comum").asBoolean(false), questoes, qconcursos,
                    textoDisciplina, lista(d.get("referencias")), tentativas);
            } else {
                disciplinaId = achada.get(0);
                jdbc.update("UPDATE edital_disciplina SET qconcursos_ids = ?, texto = ?, referencias = ?, tentativas = ?, questoes = COALESCE(?, questoes) WHERE id = ?",
                    qconcursos, textoDisciplina, lista(d.get("referencias")), tentativas, questoes, disciplinaId);
            }
            for (var a : d.path("assuntos")) {
                var chave = texto(a.get("chave"));
                var existente_ = fundir && chave != null
                    ? jdbc.queryForList("SELECT id FROM edital_assunto WHERE disciplina_id = ? AND chave = ?", Long.class, disciplinaId, chave) : List.<Long>of();
                if (existente_.isEmpty()) inserirAssunto(disciplinaId, a, versaoId, execucaoId, fundir ? fusao : null);
                else fundirAssunto(existente_.get(0), a, versaoId, execucaoId, fusao);
            }
            for (var t : d.path("trechos_sem_assunto")) inserirTrecho(analiseId, disciplinaId, "SEM_ASSUNTO", t, fundir);
        }
        for (var t : raiz.path("linhas_fora")) inserirTrecho(analiseId, null, "LINHA_FORA", t, fundir);

        if (fundir) {
            // O que veio de uma entrega e não veio nesta: marcado, nunca removido (D-89). O que a pessoa criou (execucao_id nulo) fica.
            fusao.partesQueSumiram += jdbc.update("""
                UPDATE edital_parte SET aparece = false
                 WHERE aparece AND estado <> 'DESCARTADA' AND execucao_id IS NOT NULL AND execucao_id <> ?
                   AND assunto_edital_id IN (SELECT a.id FROM edital_assunto a JOIN edital_disciplina d ON d.id = a.disciplina_id WHERE d.analise_id = ?)""",
                execucaoId, analiseId);
            jdbc.update("""
                UPDATE edital_assunto SET aparece = false
                 WHERE aparece AND execucao_id IS NOT NULL AND execucao_id <> ?
                   AND disciplina_id IN (SELECT id FROM edital_disciplina WHERE analise_id = ?)""", execucaoId, analiseId);
        }

        analise.setStatus(StatusAnaliseEdital.EM_CONFERENCIA);
        analise.setEtapaFalha(null);
        analise.setErro(null);
        analise.setReprocessar(false);
        analises.save(analise);
        return resumo(analiseId, execucaoId, false, fusao);
    }

    private void inserirAssunto(long disciplinaId, JsonNode a, long versaoId, long execucaoId, Fusao fusao) {
        var chave = texto(a.get("chave"));
        var textoAssunto = texto(a.get("texto"));
        if (chave == null || textoAssunto == null) {
            throw new ValidationException("Assunto sem chave ou sem texto.", "ENTREGA_INVALIDA", "assuntos");
        }
        long assuntoId = jdbc.queryForObject("""
            INSERT INTO edital_assunto (disciplina_id, chave, ordem, texto, erros, alertas, execucao_id)
            VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, disciplinaId, chave, a.path("ordem").asInt(0), textoAssunto,
            lista(a.get("erros")), lista(a.get("alertas")), execucaoId);
        for (var p : a.path("partes")) {
            inserirParte(assuntoId, p, versaoId, execucaoId);
            if (fusao != null) fusao.partesNovas++;
        }
    }

    private long inserirParte(long assuntoId, JsonNode p, long versaoId, long execucaoId) {
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
        return parteId;
    }

    /** Assunto que já existe (mesma chave): a parte decidida ou aceita nunca muda (D-86); a por conferir recebe a leitura nova. */
    private void fundirAssunto(long assuntoEditalId, JsonNode a, long versaoId, long execucaoId, Fusao fusao) {
        var aceito = jdbc.queryForObject("SELECT aceito FROM edital_assunto WHERE id = ?", Boolean.class, assuntoEditalId);
        if (aceito) {
            jdbc.update("UPDATE edital_assunto SET aparece = true, execucao_id = ? WHERE id = ?", execucaoId, assuntoEditalId);
        } else {
            jdbc.update("UPDATE edital_assunto SET aparece = true, execucao_id = ?, erros = ?, alertas = ? WHERE id = ?",
                execucaoId, lista(a.get("erros")), lista(a.get("alertas")), assuntoEditalId);
        }
        for (var p : a.path("partes")) {
            var chaveParte = texto(p.get("chave"));
            var achada = chaveParte == null ? List.<Long>of()
                : jdbc.queryForList("SELECT id FROM edital_parte WHERE assunto_edital_id = ? AND chave = ?", Long.class, assuntoEditalId, chaveParte);
            if (achada.isEmpty()) {
                inserirParte(assuntoEditalId, p, versaoId, execucaoId);
                fusao.partesNovas++;
                continue;
            }
            long parteId = achada.get(0);
            var linha = jdbc.queryForMap("SELECT estado FROM edital_parte WHERE id = ?", parteId);
            var estado = (String) linha.get("estado");
            var ligacao = p.path("ligacao");
            var nos = nosDe(ligacao);
            var natureza = nos.isEmpty() ? null : natureza(texto(ligacao.get("natureza")));
            var origem = origem(texto(ligacao.get("origem")));
            if ("DESCARTADA".equals(estado)) {
                jdbc.update("UPDATE edital_parte SET execucao_id = ? WHERE id = ?", execucaoId, parteId);
            } else if ("PROPOSTA".equals(estado)) {
                jdbc.update("DELETE FROM edital_parte_no WHERE parte_id = ?", parteId);
                for (int i = 0; i < nos.size(); i++) {
                    jdbc.update("INSERT INTO edital_parte_no (parte_id, proposto, ordem, no_id) VALUES (?, false, ?, ?)", parteId, i + 1, nos.get(i));
                }
                jdbc.update("""
                    UPDATE edital_parte SET aparece = true, execucao_id = ?, natureza = ?, origem = ?, prop_natureza = ?, prop_origem = ?,
                           arvore_versao_id = ?, no_mudou = false, estado = ? WHERE id = ?""",
                    execucaoId, natureza, origem, natureza, origem, versaoId, "DECISAO".equals(origem) ? "DECIDIDA" : "PROPOSTA", parteId);
            } else {
                // decidida ou aceita: nada muda. Se a IA propôs outro nó, fica de sugestão e conta em "a IA mudou" (D-95).
                var atuais = jdbc.queryForList("SELECT no_id FROM edital_parte_no WHERE parte_id = ? AND NOT proposto ORDER BY ordem", Long.class, parteId);
                boolean difere = !new java.util.HashSet<>(atuais).equals(new java.util.HashSet<>(nos));
                jdbc.update("DELETE FROM edital_parte_no WHERE parte_id = ? AND proposto", parteId);
                if (difere) {
                    for (int i = 0; i < nos.size(); i++) {
                        jdbc.update("INSERT INTO edital_parte_no (parte_id, proposto, ordem, no_id) VALUES (?, true, ?, ?)", parteId, i + 1, nos.get(i));
                    }
                    fusao.iaMudou++;
                }
                jdbc.update("UPDATE edital_parte SET aparece = true, execucao_id = ?, ia_mudou = ?, prop_natureza = ?, prop_origem = ? WHERE id = ?",
                    execucaoId, difere, natureza, origem, parteId);
            }
        }
    }

    /** Na fusão, trecho igual a um que já existe (aberto ou resolvido) não se repete. */
    private void inserirTrecho(long analiseId, Long disciplinaId, String tipo, JsonNode t, boolean fundir) {
        var paginas = lista(t.isObject() ? t.get("paginas") : null);
        var linhas = t.isObject() ? lista(t.get("linhas")) : json.writeValueAsString(List.of(t.asString()));
        if (fundir && jdbc.queryForObject("""
                SELECT count(*) FROM edital_trecho WHERE analise_id = ? AND tipo = ? AND linhas = ?
                   AND disciplina_id IS NOT DISTINCT FROM ?""", Integer.class, analiseId, tipo, linhas, disciplinaId) > 0) {
            return;
        }
        jdbc.update("INSERT INTO edital_trecho (analise_id, disciplina_id, tipo, paginas, linhas) VALUES (?, ?, ?, ?, ?)",
            analiseId, disciplinaId, tipo, paginas, linhas);
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

    private ResumoEntrega resumo(long analiseId, long execucaoId, boolean repetida, Fusao fusao) {
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
        return new ResumoEntrega(execucaoId, repetida, disciplinas, assuntos, partes, semNo, trechos, fusao.partesNovas, fusao.partesQueSumiram, fusao.iaMudou);
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
