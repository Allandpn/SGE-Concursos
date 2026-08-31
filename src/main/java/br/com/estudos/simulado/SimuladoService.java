package br.com.estudos.simulado;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * docs/SPRINT-8-SIMULADO.md §3. Tudo-ou-nada: simulado e resultados numa
 * transação só, mesma disciplina de D-05/D-45 (tenta gravar, traduz erro do
 * banco, nunca pré-verifica).
 */
@Service
public class SimuladoService {

    private final SimuladoRepository simuladoRepository;
    private final ResultadoSimuladoRepository resultadoSimuladoRepository;
    private final DisciplinaRepository disciplinaRepository;

    public SimuladoService(
            SimuladoRepository simuladoRepository,
            ResultadoSimuladoRepository resultadoSimuladoRepository,
            DisciplinaRepository disciplinaRepository) {
        this.simuladoRepository = simuladoRepository;
        this.resultadoSimuladoRepository = resultadoSimuladoRepository;
        this.disciplinaRepository = disciplinaRepository;
    }

    /**
     * `data`/`duracaoMinutos`/os campos de cada resultado são validados eager,
     * não traduzidos do banco — são `NOT NULL`/`CHECK` sem `D-xx`, e valor
     * omitido é erro plausível do cliente (mesmo padrão de
     * `SessaoService.exigirContagemDeQuestoes`). Tudo validado **antes** de
     * gravar qualquer coisa: falhar no meio do loop funcionaria igual (o
     * `@Transactional` desfaz), mas validar primeiro evita depender disso.
     */
    @Transactional
    public SimuladoRegistrado registrar(SimuladoRequest request) {
        if (request.data() == null) {
            throw new ValidationException("Data é obrigatória.", "DATA_OBRIGATORIA", "data");
        }
        if (request.duracaoMinutos() == null) {
            throw new ValidationException("Duração é obrigatória.", "DURACAO_OBRIGATORIA", "duracaoMinutos");
        }
        if (request.resultados() == null || request.resultados().isEmpty()) {
            throw new ValidationException(
                "Pelo menos um resultado por disciplina é obrigatório.", "RESULTADOS_OBRIGATORIOS", "resultados");
        }
        for (var resultadoRequest : request.resultados()) {
            validarResultado(resultadoRequest);
        }

        var simulado = new Simulado();
        simulado.setData(request.data());
        simulado.setDuracaoMinutos(request.duracaoMinutos());
        simulado = simuladoRepository.save(simulado);

        var resultados = new ArrayList<ResultadoSimulado>();
        for (var resultadoRequest : request.resultados()) {
            var resultado = new ResultadoSimulado();
            resultado.setSimulado(simulado);
            resultado.setDisciplina(disciplinaRepository.getReferenceById(resultadoRequest.disciplinaId()));
            resultado.setFormato(resultadoRequest.formato());
            resultado.setQuestoesCorretas(resultadoRequest.questoesCorretas());
            resultado.setQuestoesTotal(resultadoRequest.questoesTotal());

            try {
                resultados.add(resultadoSimuladoRepository.save(resultado));
            } catch (DataIntegrityViolationException e) {
                throw traduzirViolacaoDeIntegridade(e);
            }
        }

        return new SimuladoRegistrado(simulado, resultados);
    }

    @Transactional(readOnly = true)
    public List<Simulado> listar() {
        return simuladoRepository.findAllByOrderByDataDesc();
    }

    @Transactional(readOnly = true)
    public List<ResultadoSimulado> listarResultados(Long simuladoId) {
        return resultadoSimuladoRepository.findBySimuladoId(simuladoId);
    }

    /** GET /api/simulados (§5) — duas consultas, não N+1: uma pelos simulados, uma pelos resultados de todos eles. */
    @Transactional(readOnly = true)
    public List<SimuladoRegistrado> listarComResultados() {
        var simulados = simuladoRepository.findAllByOrderByDataDesc();
        var ids = simulados.stream().map(Simulado::getId).toList();
        var resultadosPorSimulado = resultadoSimuladoRepository.findBySimuladoIdIn(ids).stream()
            .collect(Collectors.groupingBy(r -> r.getSimulado().getId()));

        return simulados.stream()
            .map(s -> new SimuladoRegistrado(s, resultadosPorSimulado.getOrDefault(s.getId(), List.of())))
            .toList();
    }

    private void validarResultado(ResultadoSimuladoRequest resultadoRequest) {
        if (resultadoRequest.disciplinaId() == null) {
            throw new ValidationException("disciplinaId é obrigatório em cada resultado.", "DISCIPLINA_OBRIGATORIA", "disciplinaId");
        }
        if (resultadoRequest.formato() == null) {
            throw new ValidationException("Formato é obrigatório em cada resultado.", "FORMATO_OBRIGATORIO", "formato");
        }
        if (resultadoRequest.questoesCorretas() == null || resultadoRequest.questoesTotal() == null) {
            throw new ValidationException(
                "questoesCorretas e questoesTotal são obrigatórios em cada resultado.", "QUESTOES_OBRIGATORIAS", "questoesTotal");
        }
    }

    private RuntimeException traduzirViolacaoDeIntegridade(DataIntegrityViolationException e) {
        var nomeRestricao = e.getMostSpecificCause().getMessage();
        if (nomeRestricao == null) throw e;

        if (nomeRestricao.contains("fk_resultado_simulado_d13_disciplina")) {
            return new NotFoundException("Não existe uma disciplina para esse resultado.", "DISCIPLINA_INEXISTENTE");
        }
        if (nomeRestricao.contains("ux_resultado_simulado_simulado_disciplina")) {
            return new ConflictException(
                "Esta disciplina já tem resultado neste simulado.", "DISCIPLINA_DUPLICADA_NO_SIMULADO");
        }

        throw e;
    }
}
