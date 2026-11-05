package br.com.estudos.captura;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.sessao.SessaoRequest;
import br.com.estudos.sessao.SessaoService;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * A captura do QConcursos (01_DOMINIO §3.28, D-166 a D-169, D-171; ADR-063): receber a questão, achar o assunto, a caixa em Hoje, o
 * lote preenchido e o registro. A questão capturada não é sessão: só vira sessão de questões no registro.
 */
@Service
public class CapturaService {

    public record Enviada(String codigo, Boolean acertou, Instant resolvidaEm, String disciplina, List<String> assuntos, List<Long> nos,
        String banca, Integer ano, String alternativa, String gabarito, String endereco) {}

    public record Recebida(boolean ok, boolean repetida, String assunto) {}

    public record Diagnostico(String motivo, String endereco, String recorte) {}

    public record GrupoCaixa(Long assuntoId, String assunto, String nomeQc, int questoes, LocalDate primeiroDia, LocalDate ultimoDia) {}

    public record Desde(int feitas, int certas, List<Long> ids, String formato) {}

    public record Registrado(int feitas, int certas, List<Long> sessoes) {}

    public record DiagnosticoRecebido(long id, String chave, String motivo, String endereco, String recorte, Instant criadoEm) {}

    private static final Pattern CODIGO = Pattern.compile("^Q[0-9]{4,10}$");

    private final JdbcTemplate jdbc;
    private final SessaoService sessoes;
    private final Clock clock;

    public CapturaService(JdbcTemplate jdbc, SessaoService sessoes, Clock clock) {
        this.jdbc = jdbc;
        this.sessoes = sessoes;
        this.clock = clock;
    }

    // --- O envio (a chave) ---------------------------------------------------------------------------------------------

    /** D-166: grava a questão; o mesmo Q no mesmo dia não duplica. D-167: o assunto. */
    @Transactional
    public Recebida receber(long chaveId, Enviada q) {
        if (q == null || q.codigo() == null || !CODIGO.matcher(q.codigo().trim()).matches()) {
            throw new ValidationException("O número da questão (Q…) não veio.", "CAPTURA_SEM_CODIGO", "codigo");
        }
        if (q.acertou() == null) throw new ValidationException("Faltou dizer se acertou.", "CAPTURA_SEM_ACERTO", "acertou");
        var quando = q.resolvidaEm() == null ? Instant.now(clock) : q.resolvidaEm();
        var dia = LocalDate.ofInstant(quando, clock.getZone());
        var assuntos = q.assuntos() == null ? List.<String>of() : q.assuntos().stream().map(s -> corta(s, 300)).filter(s -> s != null && !s.isBlank()).limit(10).toList();
        var nos = q.nos() == null ? List.<Long>of() : q.nos().stream().filter(n -> n != null).limit(10).toList();
        var assunto = acharAssunto(assuntos, nos);
        var ids = jdbc.queryForList("""
            INSERT INTO questao_capturada (codigo, acertou, resolvida_em, dia, disciplina_qc, assuntos_qc, nos_qc, banca, ano, alternativa,
                                           gabarito, formato, endereco, chave_id, assunto_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (codigo, dia) DO NOTHING RETURNING id""", Long.class,
            q.codigo().trim(), q.acertou(), java.sql.Timestamp.from(quando), dia, corta(q.disciplina(), 300), assuntos.toArray(String[]::new),
            nos.toArray(Long[]::new), corta(q.banca(), 200), q.ano(), corta(q.alternativa(), 40), corta(q.gabarito(), 40), formato(q.gabarito()),
            corta(q.endereco(), 2000), chaveId, assunto);
        var nome = assunto == null ? null : jdbc.queryForObject("SELECT nome FROM assunto WHERE id = ?", String.class, assunto);
        return new Recebida(true, ids.isEmpty(), nome);
    }

    /** D-171. */
    @Transactional
    public void diagnostico(long chaveId, Diagnostico d) {
        if (d == null || d.recorte() == null || d.recorte().isBlank()) {
            throw new ValidationException("O recorte veio vazio.", "DIAGNOSTICO_VAZIO", "recorte");
        }
        jdbc.update("INSERT INTO captura_diagnostico (chave_id, motivo, endereco, recorte) VALUES (?, ?, ?, ?)", chaveId,
            corta(d.motivo() == null || d.motivo().isBlank() ? "sem motivo" : d.motivo(), 300), corta(d.endereco(), 2000), corta(d.recorte(), 20000));
    }

    /**
     * D-167: o par aprendido (do assunto mais específico ao mais geral); senão o nó da árvore vigente, por número ou nome, ligado pela
     * conferência a um único assunto ativo do SGE; senão nenhum.
     */
    Long acharAssunto(List<String> assuntosQc, List<Long> nosQc) {
        var nomes = new ArrayList<>(assuntosQc);
        java.util.Collections.reverse(nomes);
        for (var n : nomes) {
            var par = jdbc.queryForList("""
                SELECT p.assunto_id FROM qconcursos_par p JOIN assunto a ON a.id = p.assunto_id WHERE p.nome_norm = ? AND a.ativo""",
                Long.class, normalizar(n));
            if (!par.isEmpty()) return par.get(0);
        }
        var candidatos = new LinkedHashSet<Long>(nosQc);
        for (var n : nomes) {
            candidatos.addAll(jdbc.queryForList("""
                SELECT n.no_id FROM no_arvore n JOIN arvore_versao v ON v.id = n.versao_id
                WHERE v.vigente AND n.no_id > 0
                  AND trim(regexp_replace(lower(translate(n.nome, 'áàâãäéèêëíìîïóòôõöúùûüçÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇ',
                      'aaaaaeeeeiiiiooooouuuucAAAAAEEEEIIIIOOOOOUUUUC')), '\\s+', ' ', 'g')) = ?""", Long.class, normalizar(n)));
        }
        for (var no : candidatos) {
            var doSge = jdbc.queryForList("""
                SELECT DISTINCT ea.assunto_id FROM edital_parte_no pn
                JOIN edital_parte ep ON ep.id = pn.parte_id AND NOT pn.proposto
                JOIN edital_assunto ea ON ea.id = ep.assunto_edital_id
                JOIN assunto a ON a.id = ea.assunto_id
                WHERE pn.no_id = ? AND ep.estado IN ('ACEITA', 'DECIDIDA') AND ep.aparece AND a.ativo""", Long.class, no);
            if (doSge.size() == 1) return doSge.get(0);
        }
        return null;
    }

    // --- A caixa em Hoje (D-169) -----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<GrupoCaixa> caixa() {
        var com = jdbc.query("""
            SELECT q.assunto_id, a.nome, count(*), min(q.dia), max(q.dia) FROM questao_capturada q JOIN assunto a ON a.id = q.assunto_id
            WHERE q.situacao = 'NA_CAIXA' GROUP BY q.assunto_id, a.nome ORDER BY max(q.resolvida_em) DESC""",
            (rs, n) -> new GrupoCaixa(rs.getLong(1), rs.getString(2), null, rs.getInt(3), rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class)));
        var sem = jdbc.query("""
            SELECT coalesce(q.assuntos_qc[array_length(q.assuntos_qc, 1)], q.disciplina_qc, 'sem nome'), count(*), min(q.dia), max(q.dia)
            FROM questao_capturada q WHERE q.situacao = 'NA_CAIXA' AND q.assunto_id IS NULL
            GROUP BY 1 ORDER BY max(q.resolvida_em) DESC""",
            (rs, n) -> new GrupoCaixa(null, null, rs.getString(1), rs.getInt(2), rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class)));
        var todos = new ArrayList<>(com);
        todos.addAll(sem);
        return todos;
    }

    /** D-167: escolher o assunto de um nome do QC grava o par e leva junto as que estão na caixa com esse nome. */
    @Transactional
    public int parear(String nomeQc, long assuntoId) {
        if (nomeQc == null || nomeQc.isBlank()) throw new ValidationException("Falta o nome do assunto no QConcursos.", "PAR_SEM_NOME", "nomeQc");
        if (jdbc.queryForObject("SELECT count(*) FROM assunto WHERE id = ? AND ativo", Integer.class, assuntoId) == 0) {
            throw new NotFoundException("Assunto não encontrado.", "ASSUNTO_INEXISTENTE");
        }
        jdbc.update("""
            INSERT INTO qconcursos_par (nome_norm, nome, assunto_id) VALUES (?, ?, ?)
            ON CONFLICT (nome_norm) DO UPDATE SET nome = EXCLUDED.nome, assunto_id = EXCLUDED.assunto_id""", normalizar(nomeQc), corta(nomeQc, 300), assuntoId);
        return jdbc.update("""
            UPDATE questao_capturada SET assunto_id = ? WHERE situacao = 'NA_CAIXA' AND assunto_id IS NULL
            AND coalesce(assuntos_qc[array_length(assuntos_qc, 1)], disciplina_qc, 'sem nome') = ?""", assuntoId, nomeQc);
    }

    /** D-169: descartar tira da caixa, sem apagar. */
    @Transactional
    public int descartar(Long assuntoId, String nomeQc) {
        if (assuntoId != null) {
            return jdbc.update("UPDATE questao_capturada SET situacao = 'DESCARTADA' WHERE situacao = 'NA_CAIXA' AND assunto_id = ?", assuntoId);
        }
        return jdbc.update("""
            UPDATE questao_capturada SET situacao = 'DESCARTADA' WHERE situacao = 'NA_CAIXA' AND assunto_id IS NULL
            AND coalesce(assuntos_qc[array_length(assuntos_qc, 1)], disciplina_qc, 'sem nome') = ?""", nomeQc);
    }

    /**
     * D-169: registra as da caixa de um assunto — a previsão já feita, sem ter visto o acerto; uma sessão por formato, com a data da
     * resolução mais recente e o tempo por questão (ou o informado).
     */
    @Transactional
    public Registrado registrarCaixa(long assuntoId, Short previsao, Integer tempoMinutos) {
        if (previsao == null) throw new ValidationException("Faltou a previsão.", "CAIXA_SEM_PREVISAO", "previsaoPercentual");
        var ids = jdbc.queryForList("SELECT id FROM questao_capturada WHERE situacao = 'NA_CAIXA' AND assunto_id = ? ORDER BY resolvida_em",
            Long.class, assuntoId);
        if (ids.isEmpty()) throw new BusinessRuleException("Não há questões deste assunto na caixa.", "CAIXA_VAZIA");
        return registrar(assuntoId, ids, previsao, tempoMinutos);
    }

    /** D-168: as capturadas do assunto desde a saída pelo atalho, para preencher o lote. */
    @Transactional(readOnly = true)
    public Desde desde(long assuntoId, Instant desde) {
        var linhas = jdbc.queryForList("""
            SELECT id, acertou, formato FROM questao_capturada WHERE situacao = 'NA_CAIXA' AND assunto_id = ? AND resolvida_em >= ?
            ORDER BY resolvida_em""", assuntoId, java.sql.Timestamp.from(desde));
        var ids = linhas.stream().map(l -> ((Number) l.get("id")).longValue()).toList();
        var certas = (int) linhas.stream().filter(l -> Boolean.TRUE.equals(l.get("acertou"))).count();
        var formatos = linhas.stream().map(l -> (String) l.get("formato")).filter(f -> f != null).distinct().toList();
        return new Desde(linhas.size(), certas, ids, formatos.size() == 1 ? formatos.get(0) : null);
    }

    /** D-168: registrado o lote, as capturadas que o preencheram saem da caixa, ligadas à sessão. */
    @Transactional
    public int marcar(List<Long> ids, long sessaoId) {
        if (ids == null || ids.isEmpty()) return 0;
        return jdbc.update("UPDATE questao_capturada SET situacao = 'REGISTRADA', sessao_id = ? WHERE id = ANY (?) AND situacao = 'NA_CAIXA'",
            sessaoId, ids.toArray(Long[]::new));
    }

    private Registrado registrar(long assuntoId, List<Long> ids, short previsao, Integer tempoMinutos) {
        var linhas = jdbc.queryForList("SELECT id, acertou, coalesce(formato, 'MULTIPLA_ESCOLHA') AS formato, dia FROM questao_capturada WHERE id = ANY (?)",
            (Object) ids.toArray(Long[]::new));
        var porQuestao = Double.parseDouble(jdbc.queryForObject("SELECT valor FROM parametro WHERE chave = 'minutos_por_questao'", String.class));
        var sessoesCriadas = new ArrayList<Long>();
        int feitas = 0, certas = 0;
        var formatos = linhas.stream().map(l -> (String) l.get("formato")).distinct().sorted().toList();
        for (var formato : formatos) {
            var doFormato = linhas.stream().filter(l -> formato.equals(l.get("formato"))).toList();
            int n = doFormato.size();
            int c = (int) doFormato.stream().filter(l -> Boolean.TRUE.equals(l.get("acertou"))).count();
            var dia = doFormato.stream().map(l -> ((java.sql.Date) l.get("dia")).toLocalDate()).max(LocalDate::compareTo).orElseThrow();
            int minutos = tempoMinutos != null && formatos.size() == 1 ? tempoMinutos : Math.max(1, (int) Math.round(n * porQuestao));
            var sessao = sessoes.registrar(new SessaoRequest(assuntoId, TipoSessao.QUESTOES, dia, minutos, c, n, FormatoBanca.valueOf(formato),
                previsao, null, null, UUID.randomUUID(), null, List.of()));
            marcar(doFormato.stream().map(l -> ((Number) l.get("id")).longValue()).toList(), sessao.getId());
            sessoesCriadas.add(sessao.getId());
            feitas += n;
            certas += c;
        }
        return new Registrado(feitas, certas, sessoesCriadas);
    }

    // --- Ajustes: os diagnósticos (D-171) --------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DiagnosticoRecebido> diagnosticos() {
        return jdbc.query("""
            SELECT d.id, c.nome, d.motivo, d.endereco, d.recorte, d.criado_em FROM captura_diagnostico d JOIN captura_chave c ON c.id = d.chave_id
            ORDER BY d.id DESC LIMIT 50""",
            (rs, n) -> new DiagnosticoRecebido(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toInstant()));
    }

    @Transactional
    public void apagarDiagnostico(long id) {
        jdbc.update("DELETE FROM captura_diagnostico WHERE id = ?", id);
    }

    static String formato(String gabarito) {
        if (gabarito == null) return null;
        var g = normalizar(gabarito);
        if (g.equals("certo") || g.equals("errado")) return "CERTO_ERRADO";
        if (g.matches("^[a-e]$")) return "MULTIPLA_ESCOLHA";
        return null;
    }

    static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String corta(String s, int max) {
        if (s == null) return null;
        var t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
