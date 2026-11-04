package br.com.estudos.diagnostico;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.frente.FrenteService;
import br.com.estudos.shared.enums.FaseAssunto;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * O diagnóstico do edital (01_DOMINIO §3.21; D-137, D-138, D-139; docs/SPRINT-20-DIAGNOSTICO-EDITAL.md). Só leitura: abrir e
 * simular não gravam nada (D-16).
 */
@Service
public class DiagnosticoService {

    public record Faixa(int assuntos, int pontosPct) {}

    public record DisciplinaDiagnostico(long disciplinaId, String nome, Integer questoes, BigDecimal pontos,
        int consolidado, int escada, int estudo, int nunca, int total) {}

    public record ForaDoRitmo(long assuntoId, String nome, String peso, int minutos) {}

    public record OQueCabe(LocalDate data, boolean simulada, long dias, BigDecimal ritmo, boolean ritmoMedido, int nuncaVistos,
        int cabem, LocalDate todosVistosEm, List<ForaDoRitmo> ficamDeFora, int minutosDeFora) {}

    public record DiagnosticoResponse(long concursoId, String nome, LocalDate dataProva, int total, Faixa consolidado,
        Faixa encaminhado, List<DisciplinaDiagnostico> disciplinas, int arquivados, int foraDoEdital, BigDecimal ritmoMedido,
        OQueCabe cabe) {}

    private enum Fase { CONSOLIDADO, ESCADA, ESTUDO, NUNCA }

    private record Coberto(long assuntoId, String nome, boolean ativo, long disciplinaId, String disciplina, String peso, int ordem) {}

    private final JdbcTemplate jdbc;
    private final AssuntoRepository assuntos;
    private final FrenteService frente;
    private final Clock clock;

    public DiagnosticoService(JdbcTemplate jdbc, AssuntoRepository assuntos, FrenteService frente, Clock clock) {
        this.jdbc = jdbc;
        this.assuntos = assuntos;
        this.frente = frente;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DiagnosticoResponse diagnostico(long concursoId, LocalDate dataSimulada, BigDecimal ritmoSimulado) {
        var concurso = jdbc.query("SELECT nome, data_prova FROM concurso WHERE id = ?",
            (rs, n) -> new Object[] {rs.getString(1), rs.getObject(2, LocalDate.class)}, concursoId);
        if (concurso.isEmpty()) {
            throw new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE");
        }
        var hoje = LocalDate.now(clock);
        if (dataSimulada != null && !dataSimulada.isAfter(hoje)) {
            throw new ValidationException("A data simulada precisa ser futura.", "DATA_SIMULADA_INVALIDA", "data");
        }
        if (ritmoSimulado != null && ritmoSimulado.signum() <= 0) {
            throw new ValidationException("O ritmo precisa ser positivo.", "RITMO_INVALIDO", "ritmo");
        }

        var cobertura = jdbc.query("""
            SELECT ca.assunto_id, a.nome, a.ativo, ca.disciplina_id, d.nome AS disciplina, ca.peso, ca.ordem
            FROM concurso_assunto ca JOIN assunto a ON a.id = ca.assunto_id JOIN disciplina d ON d.id = ca.disciplina_id
            WHERE ca.concurso_id = ?
            ORDER BY CASE ca.peso WHEN 'ALTO' THEN 0 WHEN 'MEDIO' THEN 1 ELSE 2 END, ca.ordem, ca.assunto_id""",
            (rs, n) -> new Coberto(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getInt(7)),
            concursoId);
        var ativos = cobertura.stream().filter(Coberto::ativo).toList();
        var fases = fases(ativos.stream().map(Coberto::assuntoId).toList());

        // D-138: pontos da régua e questões do edital, por disciplina da cobertura.
        var pontos = new HashMap<Long, BigDecimal>();
        jdbc.query("SELECT disciplina_id, pontos FROM concurso_pontuacao WHERE concurso_id = ?",
            rs -> { pontos.put(rs.getLong(1), rs.getBigDecimal(2)); }, concursoId);
        var questoes = questoesPorDisciplina(concursoId);

        var porDisciplina = new LinkedHashMap<Long, List<Coberto>>();
        ativos.stream().sorted((x, y) -> x.disciplina().compareToIgnoreCase(y.disciplina()))
            .forEach(c -> porDisciplina.computeIfAbsent(c.disciplinaId(), k -> new ArrayList<>()).add(c));
        var entradas = porDisciplina.entrySet().stream()
            .map(e -> new Diagnostico.Disciplina(e.getKey(), pontos.getOrDefault(e.getKey(), BigDecimal.ONE),
                questoes.get(e.getKey()), e.getValue().stream().map(Coberto::assuntoId).toList()))
            .toList();
        var valores = Diagnostico.valores(entradas);
        var valorTotal = valores.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        var disciplinas = porDisciplina.entrySet().stream().map(e -> {
            var lista = e.getValue();
            return new DisciplinaDiagnostico(e.getKey(), lista.get(0).disciplina(), questoes.get(e.getKey()),
                pontos.getOrDefault(e.getKey(), BigDecimal.ONE).stripTrailingZeros(),
                contar(lista, fases, Fase.CONSOLIDADO), contar(lista, fases, Fase.ESCADA), contar(lista, fases, Fase.ESTUDO),
                contar(lista, fases, Fase.NUNCA), lista.size());
        }).toList();

        var consolidados = ativos.stream().filter(c -> fases.get(c.assuntoId()) == Fase.CONSOLIDADO).toList();
        var encaminhados = ativos.stream().filter(c -> fases.get(c.assuntoId()) == Fase.CONSOLIDADO || fases.get(c.assuntoId()) == Fase.ESCADA).toList();

        var coberto = new HashSet<Long>();
        cobertura.forEach(c -> coberto.add(c.assuntoId()));
        var foraDoEdital = jdbc.queryForList("""
            SELECT a.id FROM assunto a JOIN disciplina d ON d.id = a.disciplina_id WHERE a.ativo AND d.ativo""", Long.class)
            .stream().filter(id -> !coberto.contains(id)).count();

        var ritmoMedido = Diagnostico.ritmo(jdbc.queryForObject("""
            SELECT count(*) FROM (SELECT s.assunto_id FROM sessao s WHERE s.ativo
                AND (s.tipo <> 'QUESTOES' OR EXISTS (SELECT 1 FROM revisao r WHERE r.assunto_id = s.assunto_id))
                GROUP BY s.assunto_id HAVING min(s.data) > ? AND min(s.data) <= ?) x""",
            Integer.class, hoje.minusDays(28), hoje));

        return new DiagnosticoResponse(concursoId, (String) concurso.get(0)[0], (LocalDate) concurso.get(0)[1], ativos.size(),
            new Faixa(consolidados.size(), Diagnostico.percentual(soma(consolidados, valores), valorTotal)),
            new Faixa(encaminhados.size(), Diagnostico.percentual(soma(encaminhados, valores), valorTotal)),
            disciplinas, cobertura.size() - ativos.size(), (int) foraDoEdital, ritmoMedido,
            cabe(ativos, fases, (LocalDate) concurso.get(0)[1], dataSimulada, ritmoSimulado, ritmoMedido, hoje));
    }

    /** D-139: nulo sem data futura; os nunca vistos já vêm na ordem do edital (peso, depois ordem). */
    private OQueCabe cabe(List<Coberto> ativos, Map<Long, Fase> fases, LocalDate dataProva, LocalDate dataSimulada,
        BigDecimal ritmoSimulado, BigDecimal ritmoMedido, LocalDate hoje) {
        var data = dataSimulada != null ? dataSimulada : (dataProva != null && dataProva.isAfter(hoje) ? dataProva : null);
        if (data == null) {
            return null;
        }
        var ritmo = ritmoSimulado != null ? ritmoSimulado.stripTrailingZeros() : ritmoMedido;
        var nunca = ativos.stream().filter(c -> fases.get(c.assuntoId()) == Fase.NUNCA).toList();
        var conta = Diagnostico.cabe(nunca.size(), hoje, data, ritmo);
        var deFora = nunca.subList(conta.cabem(), nunca.size());
        var minutos = minutosDeMaterial(deFora.stream().map(Coberto::assuntoId).toList());
        var lista = deFora.stream()
            .map(c -> new ForaDoRitmo(c.assuntoId(), c.nome(), c.peso(), minutos.getOrDefault(c.assuntoId(), 0)))
            .toList();
        return new OQueCabe(data, dataSimulada != null, conta.dias(), ritmo, ritmoSimulado == null, nunca.size(), conta.cabem(),
            conta.todosVistosEm(), lista, lista.stream().mapToInt(ForaDoRitmo::minutos).sum());
    }

    /** D-137: a fase derivada (§8), com a frente separada em em estudo (nenhuma revisão ainda) e em escada. */
    private Map<Long, Fase> fases(List<Long> ids) {
        var fases = new HashMap<Long, Fase>();
        if (ids.isEmpty()) {
            return fases;
        }
        var derivadas = frente.fasesDe(assuntos.findAllById(ids));
        Set<Long> comRevisao = new HashSet<>(jdbc.queryForList(
            "SELECT DISTINCT assunto_id FROM revisao WHERE assunto_id = ANY (?)", Long.class, (Object) ids.toArray(Long[]::new)));
        for (var id : ids) {
            var fase = derivadas.getOrDefault(id, FaseAssunto.BACKLOG);
            fases.put(id, switch (fase) {
                case CONSOLIDADO -> Fase.CONSOLIDADO;
                case BACKLOG -> Fase.NUNCA;
                case FRENTE -> comRevisao.contains(id) ? Fase.ESCADA : Fase.ESTUDO;
            });
        }
        return fases;
    }

    /** As questões que a análise mais recente do concurso deu a cada disciplina, casadas pelo nome sem acento e sem caixa. */
    private Map<Long, Integer> questoesPorDisciplina(long concursoId) {
        var questoes = new HashMap<Long, Integer>();
        jdbc.query("""
            SELECT d.id, ed.questoes
            FROM edital_disciplina ed
            JOIN disciplina d ON unaccent_imutavel(lower(d.nome)) = unaccent_imutavel(lower(ed.nome))
            WHERE ed.analise_id = (SELECT max(id) FROM analise_edital WHERE concurso_id = ?) AND ed.questoes IS NOT NULL""",
            rs -> { questoes.merge(rs.getLong(1), rs.getInt(2), Integer::sum); }, concursoId);
        return questoes;
    }

    private Map<Long, Integer> minutosDeMaterial(List<Long> ids) {
        var minutos = new HashMap<Long, Integer>();
        if (ids.isEmpty()) {
            return minutos;
        }
        jdbc.query("""
            SELECT assunto_id, coalesce(sum(tempo_estimado_min), 0) FROM segmento
            WHERE arquivado_em IS NULL AND assunto_id = ANY (?) GROUP BY assunto_id""",
            rs -> { minutos.put(rs.getLong(1), rs.getInt(2)); }, (Object) ids.toArray(Long[]::new));
        return minutos;
    }

    private static int contar(List<Coberto> lista, Map<Long, Fase> fases, Fase fase) {
        return (int) lista.stream().filter(c -> fases.get(c.assuntoId()) == fase).count();
    }

    private static BigDecimal soma(List<Coberto> lista, Map<Long, BigDecimal> valores) {
        return lista.stream().map(c -> valores.getOrDefault(c.assuntoId(), BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
