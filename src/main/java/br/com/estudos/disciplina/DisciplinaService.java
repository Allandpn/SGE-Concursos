package br.com.estudos.disciplina;

import java.util.List;

import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.ValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.revisao.RevisaoService;
import br.com.estudos.shared.exception.NotFoundException;

@Service
public class DisciplinaService {

    private final DisciplinaRepository disciplinaRepository;
    private final AssuntoRepository assuntoRepository;
    private final RevisaoService revisaoService;

    public DisciplinaService(
            DisciplinaRepository disciplinaRepository,
            AssuntoRepository assuntoRepository,
            RevisaoService revisaoService) {
        this.disciplinaRepository = disciplinaRepository;
        this.assuntoRepository = assuntoRepository;
        this.revisaoService = revisaoService;
    }

    /**
     * Cria uma disciplina. Nenhuma restrição nomeada além de
     * ck_disciplina_peso, e essa é inatingível via TipoPeso (enum) — não há
     * exceção do driver para traduzir aqui, ao contrário de AssuntoService.
     */
    @Transactional
    public Disciplina criar(DisciplinaRequest request) {
        var disciplina = new Disciplina();
        disciplina.setNome(request.nome());
        disciplina.setPeso(request.peso());
        disciplina.setAtivo(true);
        try {
            return disciplinaRepository.save(disciplina);
        }
        catch (DataIntegrityViolationException e) {
             throw traduzirViolacaoDeIntegridade(e);
        }
    }

    /** Busca uma disciplina pelo id. Lança se não existir. */
    @Transactional(readOnly = true)
    public Disciplina buscar(Long id) {
        return disciplinaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Disciplina não encontrada.", "DISCIPLINA_INEXISTENTE"));
    }

    /** Lista as disciplinas ativas. */
    @Transactional(readOnly = true)
    public List<Disciplina> listar() {
        return disciplinaRepository.findByAtivoTrue();
    }

    /** Atualiza parcialmente uma disciplina existente. */
    @Transactional
    public Disciplina atualizar(Long id, DisciplinaRequest request) {
        var disciplina = buscar(id);
        if (request.nome() != null) disciplina.setNome(request.nome());
        if (request.peso() != null) disciplina.setPeso(request.peso());
        return disciplina;
    }

    /**
     * Arquiva uma disciplina — exclusão lógica (D-18) — e cancela as revisões
     * pendentes de todos os assuntos dela (D-17, fechada na Sprint 4,
     * docs/SPRINT-4-ESCADA.md §3.4).
     */
    @Transactional
    public void arquivar(Long id) {
        var disciplina = buscar(id);
        disciplina.setAtivo(false);
        revisaoService.cancelarPendentesPorDisciplina(disciplina.getId());
    }

    /**
     * Reativa uma disciplina arquivada — operação simétrica de {@link #arquivar}
     * (D-49, 01_DOMINIO §3.4). Só restaura revisão dos assuntos que continuam
     * ativos: um assunto arquivado por conta própria (antes ou depois desta
     * disciplina) fica de fora — arquivar disciplina nunca mexeu no `ativo`
     * dele (só congelou via FrenteService), então reativar também não mexe.
     */
    @Transactional
    public void reativar(Long id) {
        var disciplina = buscar(id);
        disciplina.setAtivo(true);
        disciplinaRepository.save(disciplina);
        for (var assunto : assuntoRepository.findByDisciplinaIdAndAtivoTrue(id)) {
            revisaoService.restaurarPendenteSeCancelada(assunto.getId());
        }
    }

    private RuntimeException traduzirViolacaoDeIntegridade(DataIntegrityViolationException e) {
        var nomeRestricao = e.getMostSpecificCause().getMessage();
        if(nomeRestricao != null) {
            if (nomeRestricao.contains("ux_d47_nome_disciplina")) {
                return new ConflictException(
                        "Já existe uma disciplina com este nome",
                        "NOME_DUPLICADO"
                );
            }
        }
        throw e;
    }
}
