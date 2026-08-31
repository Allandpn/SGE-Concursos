package br.com.estudos.simulado;

import java.util.ArrayList;
import java.util.List;

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

    @Transactional
    public SimuladoRegistrado registrar(SimuladoRequest request) {
        if (request.resultados() == null || request.resultados().isEmpty()) {
            throw new ValidationException(
                "Pelo menos um resultado por disciplina é obrigatório.", "RESULTADOS_OBRIGATORIOS", "resultados");
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
        return simuladoRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ResultadoSimulado> listarResultados(Long simuladoId) {
        return resultadoSimuladoRepository.findBySimuladoId(simuladoId);
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
