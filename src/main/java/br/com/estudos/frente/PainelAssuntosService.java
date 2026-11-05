package br.com.estudos.frente;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoMapper;
import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.assunto.AssuntoResponse;
import br.com.estudos.disciplina.DisciplinaMapper;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.disciplina.DisciplinaResponse;
import br.com.estudos.shared.enums.FaseAssunto;

/**
 * A tela Assuntos numa consulta só (04_FRONTEND §8B, "As demais telas"; Sprint 40): as disciplinas ativas, cada uma com os assuntos
 * ativos, a fase derivada (D-16/D-24, em lote) e a data da revisão pendente.
 */
@Service
public class PainelAssuntosService {

    public record Item(AssuntoResponse assunto, FaseAssunto fase, LocalDate proximaRevisao) {}

    public record Grupo(DisciplinaResponse disciplina, List<Item> assuntos) {}

    private final DisciplinaRepository disciplinas;
    private final AssuntoRepository assuntos;
    private final FrenteService frente;
    private final JdbcTemplate jdbc;

    public PainelAssuntosService(DisciplinaRepository disciplinas, AssuntoRepository assuntos, FrenteService frente, JdbcTemplate jdbc) {
        this.disciplinas = disciplinas;
        this.assuntos = assuntos;
        this.frente = frente;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<Grupo> painel() {
        var todos = assuntos.listarAtivosDeDisciplinasAtivas();
        var fases = frente.fasesDe(todos);
        var revisao = new HashMap<Long, LocalDate>();
        jdbc.query("SELECT assunto_id, data_prevista FROM revisao WHERE situacao = 'PENDENTE'",
            rs -> { revisao.put(rs.getLong(1), rs.getObject(2, LocalDate.class)); });
        var porDisciplina = new HashMap<Long, List<Item>>();
        for (var a : todos) {
            porDisciplina.computeIfAbsent(a.getDisciplina().getId(), k -> new ArrayList<>())
                .add(new Item(AssuntoMapper.toResponse(a), fases.get(a.getId()), revisao.get(a.getId())));
        }
        return disciplinas.findByAtivoTrue().stream()
            .sorted(Comparator.comparing(d -> d.getId()))
            .map(d -> new Grupo(DisciplinaMapper.toResponse(d), porDisciplina.getOrDefault(d.getId(), List.of()).stream()
                .sorted(Comparator.comparing((Item i) -> i.assunto().ordem()).thenComparing(i -> i.assunto().id())).toList()))
            .toList();
    }
}
