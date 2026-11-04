package br.com.estudos.ensaio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.diagnostico.Diagnostico;
import br.com.estudos.frente.FrenteService;
import br.com.estudos.retafinal.RetaFinalConsulta;
import br.com.estudos.shared.enums.FaseAssunto;
import br.com.estudos.turno.TurnoService;

/** Os próximos 7 dias da escada (01_DOMINIO §3.25, D-157; docs/SPRINT-38-PROXIMOS-DIAS-E-ENSAIO.md §2). Só consulta (D-16). */
@Service
public class ProximosDiasService {

    static final int DIAS = 7;

    public record Revisao(long assuntoId, String nome, int nivel, boolean manutencao) {}

    public record Dia(LocalDate data, int total, boolean acimaDoTeto, boolean simulado, boolean prova, List<Revisao> revisoes) {}

    public record ProximosDias(int teto, BigDecimal ritmoSemanal, int esperandoProva, LocalDate dataProva, List<Dia> dias) {}

    private record Linha(long assuntoId, String nome, int nivel, LocalDate data) {}

    private final JdbcTemplate jdbc;
    private final FrenteService frente;
    private final RetaFinalConsulta retaFinal;
    private final AssuntoRepository assuntos;
    private final Clock clock;

    public ProximosDiasService(JdbcTemplate jdbc, FrenteService frente, RetaFinalConsulta retaFinal, AssuntoRepository assuntos, Clock clock) {
        this.jdbc = jdbc;
        this.frente = frente;
        this.retaFinal = retaFinal;
        this.assuntos = assuntos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProximosDias proximos() {
        var hoje = LocalDate.now(clock);
        var amanha = hoje.plusDays(1);
        var ultimo = hoje.plusDays(DIAS);
        var teto = Integer.parseInt(jdbc.queryForObject("SELECT valor FROM parametro WHERE chave = 'teto_diario_recuperacoes'", String.class));

        var linhas = jdbc.query("""
            SELECT r.assunto_id, a.nome, r.nivel, r.data_prevista FROM revisao r
            JOIN assunto a ON a.id = r.assunto_id JOIN disciplina d ON d.id = a.disciplina_id
            WHERE r.situacao = 'PENDENTE' AND a.ativo AND d.ativo AND r.data_prevista BETWEEN ? AND ?
            ORDER BY r.data_prevista, a.nome, r.id""",
            (rs, n) -> new Linha(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getObject(4, LocalDate.class)), amanha, ultimo);

        var ids = new HashSet<Long>();
        linhas.forEach(l -> ids.add(l.assuntoId()));
        var fases = frente.fasesDe(assuntos.findAllById(ids));
        // D-143: na reta final, a manutenção de médio e baixo espera a prova — fora dos dias, contada à parte.
        var escondidas = frente.assuntosComManutencaoEscondida();
        var ativa = retaFinal.ativa();

        var porDia = new LinkedHashMap<LocalDate, List<Revisao>>();
        for (var d = amanha; !d.isAfter(ultimo); d = d.plusDays(1)) porDia.put(d, new ArrayList<>());
        var esperando = 0;
        for (var l : linhas) {
            if (escondidas.contains(l.assuntoId())) {
                esperando++;
                continue;
            }
            porDia.get(l.data()).add(new Revisao(l.assuntoId(), l.nome(), l.nivel(), fases.get(l.assuntoId()) == FaseAssunto.CONSOLIDADO));
        }

        // D-144: o simulado da semana no fim de semana, na reta final; na semana corrente, só se ainda não houve.
        var segunda = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var domingo = segunda.plusDays(6);
        var jaHouveNaSemana = ativa.isPresent() && jdbc.queryForObject(
            "SELECT count(*) FROM simulado WHERE data BETWEEN ? AND ? AND arquivado_em IS NULL", Integer.class, segunda, hoje) > 0;
        var prova = ativa.map(RetaFinalConsulta.Ativa::dataProva).orElse(null);

        var dias = new ArrayList<Dia>();
        porDia.forEach((data, revisoes) -> {
            var simulado = ativa.isPresent() && TurnoService.diaDoSimulado(data) && !(jaHouveNaSemana && !data.isAfter(domingo))
                && (prova == null || data.isBefore(prova));
            dias.add(new Dia(data, revisoes.size(), revisoes.size() > teto, simulado, data.equals(prova), revisoes));
        });

        // D-139 com D-155: assuntos lidos pela primeira vez nas últimas 4 semanas ÷ 4.
        var novos = jdbc.queryForObject("""
            SELECT count(*) FROM (SELECT s.assunto_id FROM sessao s WHERE s.ativo
                AND (s.tipo <> 'QUESTOES' OR EXISTS (SELECT 1 FROM revisao r WHERE r.assunto_id = s.assunto_id))
                GROUP BY s.assunto_id HAVING min(s.data) > ? AND min(s.data) <= ?) x""",
            Integer.class, hoje.minusDays(28), hoje);
        return new ProximosDias(teto, Diagnostico.ritmo(novos), esperando, prova, dias);
    }
}
