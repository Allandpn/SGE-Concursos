package br.com.estudos.disciplina;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.NotFoundException;

@Service
public class DisciplinaService {

    private final DisciplinaRepository disciplinaRepository;

    public DisciplinaService(DisciplinaRepository disciplinaRepository) {
        this.disciplinaRepository = disciplinaRepository;
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
        return disciplinaRepository.save(disciplina);
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
     * Arquiva uma disciplina — exclusão lógica (D-18). Não cancela revisões
     * pendentes dos assuntos dela: D-17 é pendência documentada até a
     * Sprint 4 (docs/SPRINT-2-CADASTRO.md §7).
     */
    @Transactional
    public void arquivar(Long id) {
        buscar(id).setAtivo(false);
    }
}
