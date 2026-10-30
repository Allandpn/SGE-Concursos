package br.com.estudos.foco;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.foco.FaseFoco.Especie;
import br.com.estudos.foco.FaseFoco.Motivo;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Pomodoro (01_DOMINIO §3.15, D-98 a D-105; docs/SPRINT-29-POMODORO.md). A fase é gravada com o instante de início e o relógio é
 * sempre calculado ({@link FocoCalculo}); o relógio é o do servidor, injetado, nunca {@code Instant.now()} solto.
 */
@Service
public class FocoService {

    private static final String COLUNAS = """
        id, tentativa_id, especie, assunto_id, segmento_id, inicio, duracao_programada_s, pausado_desde,
        pausado_acumulado_s, fim, motivo_fim, aviso_fim_em, sessao_id""";

    public record Situacao(FaseFoco fase, long decorridoS, long restanteS, boolean pausada) {}

    public record Proposta(Especie especie, int duracaoS, Long assuntoId, Long segmentoId, int focosSeguidos, int focosAteLonga) {}

    public record Livres(long segundos, int minutos, List<Long> fasesIds) {}

    public record FocoDoDia(LocalDate dia, long segundos) {}

    public record Resumo(List<FocoDoDia> porDia, int completos, int encerradosAntes) {}

    public record FocoDoSegmento(Long segmentoId, int ordem, Integer estimadoMin, int realMin) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public FocoService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private Instant agora() {
        return Instant.now(clock);
    }

    // ---------- leitura e encerramento do que venceu ----------

    /** A fase em curso, depois de encerrar a que venceu. Vazio se não há nenhuma. */
    @Transactional
    public Optional<Situacao> atual() {
        encerrarVencidas();
        return emCurso().map(f -> situacao(f, agora()));
    }

    /**
     * D-99 / ADR-050 — a fase que chegou à duração programada é encerrada com {@code fim} = vencimento, seja quem for o primeiro a
     * perceber (a consulta, o início de outra fase ou a varredura). Devolve a que acabou de encerrar, se houve.
     */
    @Transactional
    public Optional<FaseFoco> encerrarVencidas() {
        var viva = emCurso();
        if (viva.isEmpty() || !FocoCalculo.venceu(viva.get(), agora())) return Optional.empty();
        var fase = viva.get();
        int linhas = jdbc.update("UPDATE fase_foco SET fim = ?, motivo_fim = 'VENCIDA' WHERE id = ? AND fim IS NULL",
            ts(FocoCalculo.vencimento(fase)), fase.id());
        return linhas == 1 ? buscar(fase.id()) : Optional.empty();
    }

    // ---------- início, pausa e fim ----------

    /**
     * Inicia uma fase. D-98: a unicidade é do índice parcial; aqui só se traduz a violação. Repetir a {@code tentativaId} devolve a
     * mesma fase (como D-45).
     */
    @Transactional
    public FaseFoco iniciar(UUID tentativaId, Especie especie, Long assuntoId, Long segmentoId) {
        if (tentativaId == null || especie == null) {
            throw new ValidationException("tentativaId e espécie são obrigatórios.", "FASE_INVALIDA", "tentativaId");
        }
        if (especie != Especie.FOCO && (assuntoId != null || segmentoId != null)) {
            throw new ValidationException("Pausa não aponta para assunto.", "FASE_INVALIDA", "assuntoId");
        }
        validarVinculo(assuntoId, segmentoId);
        encerrarVencidas();
        int duracaoS = duracaoS(especie);
        int linhas;
        try {
            linhas = jdbc.update("""
                INSERT INTO fase_foco (tentativa_id, especie, assunto_id, segmento_id, inicio, duracao_programada_s)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (tentativa_id) DO NOTHING""",
                tentativaId, especie.name(), assuntoId, segmentoId, ts(agora()), duracaoS);
        } catch (DataIntegrityViolationException e) {
            var causa = e.getMostSpecificCause().getMessage();
            if (causa != null && causa.contains("ux_fase_foco_d98_uma_em_curso")) {
                throw new BusinessRuleException("Já há uma fase em curso. Encerre-a antes de começar outra.", "FASE_EM_CURSO");
            }
            throw e;
        }
        var fase = jdbc.queryForObject("SELECT " + COLUNAS + " FROM fase_foco WHERE tentativa_id = ?", this::mapear, tentativaId);
        if (linhas == 0 && (fase.especie() != especie)) {
            throw new ValidationException("Esta tentativa já foi usada por outra fase.", "FASE_INVALIDA", "tentativaId");
        }
        return fase;
    }

    @Transactional
    public Situacao pausar() {
        var fase = exigirEmCurso();
        if (fase.especie() != Especie.FOCO) {
            throw new BusinessRuleException("Só o foco se pausa; a pausa do Pomodoro se pula.", "FASE_NAO_PAUSAVEL");
        }
        if (!fase.pausada()) {
            jdbc.update("UPDATE fase_foco SET pausado_desde = ? WHERE id = ? AND fim IS NULL AND pausado_desde IS NULL", ts(agora()), fase.id());
        }
        return situacao(buscar(fase.id()).orElseThrow(), agora());
    }

    @Transactional
    public Situacao retomar() {
        var fase = exigirEmCurso();
        if (fase.pausada()) {
            long pausaS = Math.max(0, java.time.Duration.between(fase.pausadoDesde(), agora()).toSeconds());
            jdbc.update("UPDATE fase_foco SET pausado_acumulado_s = pausado_acumulado_s + ?, pausado_desde = NULL WHERE id = ? AND fim IS NULL",
                pausaS, fase.id());
        }
        return situacao(buscar(fase.id()).orElseThrow(), agora());
    }

    /** A pessoa encerra. Se a fase já tinha vencido, quem a encerrou foi o relógio ({@code VENCIDA}), não a pessoa. */
    @Transactional
    public FaseFoco encerrar() {
        encerrarVencidas();
        var fase = exigirEmCurso();
        var agora = agora();
        long pausaS = fase.pausada() ? Math.max(0, java.time.Duration.between(fase.pausadoDesde(), agora).toSeconds()) : 0;
        jdbc.update("""
            UPDATE fase_foco SET fim = ?, motivo_fim = 'ENCERRADA', pausado_desde = NULL,
                   pausado_acumulado_s = pausado_acumulado_s + ? WHERE id = ? AND fim IS NULL""", ts(agora), pausaS, fase.id());
        return buscar(fase.id()).orElseThrow();
    }

    /** O vínculo é opcional e pode ser feito depois, antes de registrar (§3.15): vale para foco ainda sem sessão. */
    @Transactional
    public FaseFoco apontar(Long id, Long assuntoId, Long segmentoId) {
        var fase = buscar(id).orElseThrow(() -> new NotFoundException("Fase não encontrada.", "FASE_INEXISTENTE"));
        if (fase.especie() != Especie.FOCO || fase.sessaoId() != null) {
            throw new BusinessRuleException("Só um foco ainda sem sessão pode mudar de assunto.", "FASE_JA_USADA");
        }
        validarVinculo(assuntoId, segmentoId);
        jdbc.update("UPDATE fase_foco SET assunto_id = ?, segmento_id = ? WHERE id = ?", assuntoId, segmentoId, id);
        return buscar(id).orElseThrow();
    }

    // ---------- proposta (D-102) ----------

    /** Só deriva: não grava nada. Nenhuma fase começa sozinha. */
    @Transactional
    public Optional<Proposta> proposta() {
        encerrarVencidas();
        if (emCurso().isPresent()) return Optional.empty();
        var ultimas = jdbc.query("SELECT " + COLUNAS + " FROM fase_foco WHERE fim IS NOT NULL ORDER BY fim DESC, id DESC LIMIT 1", this::mapear);
        if (ultimas.isEmpty()) return Optional.empty();
        var ultima = ultimas.get(0);
        int ate = (int) parametro("pomodoro_focos_ate_longa");
        if (ultima.especie() == Especie.FOCO) {
            int seguidos = focosCompletosDesdeUltimaLonga();
            var especie = seguidos >= ate ? Especie.PAUSA_LONGA : Especie.PAUSA_CURTA;
            return Optional.of(new Proposta(especie, duracaoS(especie), null, null, seguidos, ate));
        }
        var ultimoFoco = jdbc.query("SELECT " + COLUNAS + " FROM fase_foco WHERE especie = 'FOCO' AND fim IS NOT NULL ORDER BY fim DESC, id DESC LIMIT 1", this::mapear);
        Long assunto = ultimoFoco.isEmpty() ? null : ultimoFoco.get(0).assuntoId();
        Long segmento = ultimoFoco.isEmpty() ? null : ultimoFoco.get(0).segmentoId();
        return Optional.of(new Proposta(Especie.FOCO, duracaoS(Especie.FOCO), assunto, segmento, focosCompletosDesdeUltimaLonga(), ate));
    }

    /** Focos completos (VENCIDA) do dia, depois da última pausa longa. */
    private int focosCompletosDesdeUltimaLonga() {
        var hoje = LocalDate.now(clock);
        var inicioDoDia = hoje.atStartOfDay(clock.getZone()).toInstant();
        var ultimaLonga = jdbc.query("SELECT fim FROM fase_foco WHERE especie = 'PAUSA_LONGA' AND fim IS NOT NULL AND fim >= ? ORDER BY fim DESC LIMIT 1",
            (rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant(), ts(inicioDoDia));
        var corte = ultimaLonga.isEmpty() ? inicioDoDia : ultimaLonga.get(0);
        Integer n = jdbc.queryForObject("SELECT count(*) FROM fase_foco WHERE especie = 'FOCO' AND motivo_fim = 'VENCIDA' AND fim > ?", Integer.class, ts(corte));
        return n == null ? 0 : n;
    }

    // ---------- registro (D-100, D-101) ----------

    /** Os focos encerrados do assunto que nenhuma sessão usou ainda: o que pré-preenche o tempo (D-100). */
    @Transactional(readOnly = true)
    public Livres livres(Long assuntoId) {
        var fases = jdbc.query("SELECT " + COLUNAS + " FROM fase_foco WHERE especie = 'FOCO' AND fim IS NOT NULL AND sessao_id IS NULL AND assunto_id = ? ORDER BY id",
            this::mapear, assuntoId);
        long total = fases.stream().mapToLong(f -> FocoCalculo.focoCorridoS(f, agora())).sum();
        return new Livres(total, FocoCalculo.minutos(total), fases.stream().map(FaseFoco::id).toList());
    }

    /**
     * D-101 — cada foco serve a uma sessão no máximo. Roda na transação do registro: se alguma fase não serve (de outro assunto, ainda
     * em curso, já usada), a sessão inteira não grava.
     */
    @Transactional
    public void consumir(Long sessaoId, Long assuntoId, List<Long> fasesIds) {
        if (fasesIds == null || fasesIds.isEmpty()) return;
        var unicos = fasesIds.stream().distinct().toList();
        var marcas = String.join(",", java.util.Collections.nCopies(unicos.size(), "?"));
        var args = new ArrayList<Object>();
        args.add(sessaoId);
        args.addAll(unicos);
        args.add(assuntoId);
        int linhas = jdbc.update("UPDATE fase_foco SET sessao_id = ? WHERE id IN (" + marcas
            + ") AND especie = 'FOCO' AND fim IS NOT NULL AND sessao_id IS NULL AND assunto_id = ?", args.toArray());
        if (linhas != unicos.size()) {
            throw new ValidationException("Algum foco informado não serve a esta sessão (outro assunto, em curso ou já usado).", "FOCO_INVALIDO", "fasesIds");
        }
    }

    // ---------- o que se aprende (D-105) ----------

    /** Foco por dia e focos completos × encerrados antes, no intervalo (datas locais, ambas inclusive). */
    @Transactional(readOnly = true)
    public Resumo resumo(LocalDate de, LocalDate ate) {
        var zona = clock.getZone();
        var fases = jdbc.query("SELECT " + COLUNAS + " FROM fase_foco WHERE especie = 'FOCO' AND fim IS NOT NULL AND inicio >= ? AND inicio < ? ORDER BY inicio",
            this::mapear, ts(de.atStartOfDay(zona).toInstant()), ts(ate.plusDays(1).atStartOfDay(zona).toInstant()));
        var porDia = new LinkedHashMap<LocalDate, Long>();
        int completos = 0, antes = 0;
        for (var f : fases) {
            porDia.merge(f.inicio().atZone(zona).toLocalDate(), FocoCalculo.focoCorridoS(f, agora()), Long::sum);
            if (f.motivoFim() == Motivo.VENCIDA) completos++; else antes++;
        }
        return new Resumo(porDia.entrySet().stream().map(e -> new FocoDoDia(e.getKey(), e.getValue())).toList(), completos, antes);
    }

    /** Estimado × real por segmento: a estimativa do segmento contra o foco medido nas sessões ESTUDO que o apontam. */
    @Transactional(readOnly = true)
    public List<FocoDoSegmento> porSegmento(Long assuntoId) {
        var segmentos = jdbc.queryForList("SELECT id, ordem, tempo_estimado_min FROM segmento WHERE assunto_id = ? AND arquivado_em IS NULL ORDER BY ordem", assuntoId);
        var fases = jdbc.query("""
            SELECT f.id, f.tentativa_id, f.especie, f.assunto_id, f.segmento_id, f.inicio, f.duracao_programada_s, f.pausado_desde,
                   f.pausado_acumulado_s, f.fim, f.motivo_fim, f.aviso_fim_em, f.sessao_id, s.segmento_id AS segmento_da_sessao
              FROM fase_foco f JOIN sessao s ON s.id = f.sessao_id
             WHERE s.assunto_id = ? AND s.tipo = 'ESTUDO' AND s.segmento_id IS NOT NULL AND f.especie = 'FOCO'""",
            (rs, n) -> Map.entry(rs.getLong("segmento_da_sessao"), mapear(rs, n)), assuntoId);
        var saida = new ArrayList<FocoDoSegmento>();
        for (var seg : segmentos) {
            long id = ((Number) seg.get("id")).longValue();
            long segundos = fases.stream().filter(e -> e.getKey() == id).mapToLong(e -> FocoCalculo.focoCorridoS(e.getValue(), agora())).sum();
            var estimado = seg.get("tempo_estimado_min") == null ? null : ((Number) seg.get("tempo_estimado_min")).intValue();
            saida.add(new FocoDoSegmento(id, ((Number) seg.get("ordem")).intValue(), estimado, FocoCalculo.minutos(segundos)));
        }
        return saida;
    }

    // ---------- apoio ----------

    private Optional<FaseFoco> emCurso() {
        return jdbc.query("SELECT " + COLUNAS + " FROM fase_foco WHERE fim IS NULL", this::mapear).stream().findFirst();
    }

    private FaseFoco exigirEmCurso() {
        return emCurso().orElseThrow(() -> new NotFoundException("Não há fase em curso.", "FASE_INEXISTENTE"));
    }

    private Optional<FaseFoco> buscar(Long id) {
        return jdbc.query("SELECT " + COLUNAS + " FROM fase_foco WHERE id = ?", this::mapear, id).stream().findFirst();
    }

    private Situacao situacao(FaseFoco fase, Instant agora) {
        return new Situacao(fase, FocoCalculo.decorridoS(fase, agora), FocoCalculo.restanteS(fase, agora), fase.pausada());
    }

    private void validarVinculo(Long assuntoId, Long segmentoId) {
        if (segmentoId != null && assuntoId == null) {
            throw new ValidationException("Segmento exige o assunto.", "FASE_INVALIDA", "segmentoId");
        }
        if (assuntoId != null) {
            Integer existe = jdbc.queryForObject("SELECT count(*) FROM assunto WHERE id = ?", Integer.class, assuntoId);
            if (existe == null || existe == 0) throw new NotFoundException("Não existe esse assunto.", "ASSUNTO_INEXISTENTE");
        }
        if (segmentoId != null) {
            Integer daqui = jdbc.queryForObject("SELECT count(*) FROM segmento WHERE id = ? AND assunto_id = ?", Integer.class, segmentoId, assuntoId);
            if (daqui == null || daqui == 0) {
                throw new ValidationException("O segmento não pertence a esse assunto.", "SEGMENTO_DE_OUTRO_ASSUNTO", "segmentoId");
            }
        }
    }

    private int duracaoS(Especie especie) {
        var chave = switch (especie) {
            case FOCO -> "pomodoro_foco_min";
            case PAUSA_CURTA -> "pomodoro_pausa_curta_min";
            case PAUSA_LONGA -> "pomodoro_pausa_longa_min";
        };
        return (int) Math.max(1, Math.round(parametro(chave) * 60));
    }

    private double parametro(String chave) {
        var valores = jdbc.queryForList("SELECT valor FROM parametro WHERE chave = ?", String.class, chave);
        if (valores.isEmpty()) throw new IllegalStateException("Parâmetro obrigatório ausente: " + chave);
        return Double.parseDouble(valores.get(0));
    }

    private static OffsetDateTime ts(Instant instante) {
        return OffsetDateTime.ofInstant(instante, java.time.ZoneOffset.UTC);
    }

    private FaseFoco mapear(ResultSet rs, int n) throws SQLException {
        return new FaseFoco(
            rs.getLong("id"),
            rs.getObject("tentativa_id", UUID.class),
            Especie.valueOf(rs.getString("especie")),
            (Long) rs.getObject("assunto_id"),
            (Long) rs.getObject("segmento_id"),
            rs.getObject("inicio", OffsetDateTime.class).toInstant(),
            rs.getInt("duracao_programada_s"),
            instante(rs, "pausado_desde"),
            rs.getInt("pausado_acumulado_s"),
            instante(rs, "fim"),
            rs.getString("motivo_fim") == null ? null : Motivo.valueOf(rs.getString("motivo_fim")),
            instante(rs, "aviso_fim_em"),
            (Long) rs.getObject("sessao_id"));
    }

    private static Instant instante(ResultSet rs, String coluna) throws SQLException {
        var valor = rs.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
