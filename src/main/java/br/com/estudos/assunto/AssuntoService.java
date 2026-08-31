package br.com.estudos.assunto;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.revisao.RevisaoService;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

@Service
public class AssuntoService {

    private final AssuntoRepository assuntoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final RevisaoService revisaoService;

    public AssuntoService(
            AssuntoRepository assuntoRepository,
            DisciplinaRepository disciplinaRepository,
            RevisaoService revisaoService) {
        this.assuntoRepository = assuntoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.revisaoService = revisaoService;
    }

    /**
     * Cria um assunto vinculado a uma disciplina existente.
     * Não pré-verifica nome duplicado nem disciplina existente — grava direto
     * e traduz a exceção do driver pelo nome da restrição, mesmo princípio de
     * D-05 (unicidade na persistência, nunca checagem prévia no serviço).
     * docs/SPRINT-2-CADASTRO.md §1.2, §3.1.
     */
    @Transactional
    public Assunto criar(AssuntoRequest request) {
        var assunto = new Assunto();
        assunto.setDisciplina(disciplinaRepository.getReferenceById(request.disciplinaId()));
        assunto.setNome(request.nome());
        assunto.setPeso(request.peso());
        assunto.setDificuldadePercebida(request.dificuldadePercebida());
        assunto.setOrdem(request.ordem());
        assunto.setAtivo(true);

        try {
            return assuntoRepository.save(assunto);
        } catch (DataIntegrityViolationException e) {
            throw traduzirViolacaoDeIntegridade(e);
        }
    }

    /** Busca um assunto pelo id. Lança se não existir. */
    @Transactional(readOnly = true)
    public Assunto buscar(Long id) {
        return assuntoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Assunto não encontrado.", "ASSUNTO_INEXISTENTE"));
    }

    /** Lista os assuntos ativos de uma disciplina (docs/SPRINT-2-CADASTRO.md §2). */
    @Transactional(readOnly = true)
    public List<Assunto> listar(Long disciplinaId) {
        return assuntoRepository.findByDisciplinaIdAndAtivoTrue(disciplinaId);
    }

    /**
     * Atualiza parcialmente um assunto existente — só os campos não nulos do
     * request são aplicados. `disciplinaId` não é atualizável aqui: mover um
     * assunto de disciplina não está no escopo desta sprint (§0).
     * Mesma tradução de exceção de {@link #criar}.
     */
    @Transactional
    public Assunto atualizar(Long id, AssuntoRequest request) {
        var assunto = buscar(id);
        if (request.nome() != null) assunto.setNome(request.nome());
        if (request.peso() != null) assunto.setPeso(request.peso());
        if (request.dificuldadePercebida() != null) assunto.setDificuldadePercebida(request.dificuldadePercebida());
        if (request.ordem() != null) assunto.setOrdem(request.ordem());

        try {
            // saveAndFlush, não save: a entidade já é gerenciada (veio de
            // buscar() na mesma transação), então save() sozinho não força
            // escrita — o Hibernate só flusharia no commit, depois deste
            // método já ter retornado, e o catch abaixo nunca capturaria nada.
            return assuntoRepository.saveAndFlush(assunto);
        } catch (DataIntegrityViolationException e) {
            throw traduzirViolacaoDeIntegridade(e);
        }
    }

    /**
     * Arquiva um assunto — exclusão lógica (D-18) — e cancela a revisão
     * pendente dele (D-17, fechada na Sprint 4, docs/SPRINT-4-ESCADA.md §3.4;
     * era pendência documentada em docs/SPRINT-2-CADASTRO.md §7).
     */
    @Transactional
    public void arquivar(Long id) {
        var assunto = buscar(id);
        assunto.setAtivo(false);
        revisaoService.cancelarPendentePorAssunto(assunto.getId());
    }

    /**
     * Traduz a exceção do driver pelo nome da restrição violada (§3.1).
     */
    private RuntimeException traduzirViolacaoDeIntegridade(DataIntegrityViolationException e) {
        var nomeRestricao = e.getMostSpecificCause().getMessage();

        if(nomeRestricao != null) {
            if (nomeRestricao.contains("ux_assunto_j1_nome_por_disciplina")) {
                return new ConflictException(
                    "Já existe um assunto com este nome nesta disciplina.",
                    "NOME_DUPLICADO"
                );
            }

            if (nomeRestricao.contains("fk_assunto_disciplina")) {
                return new NotFoundException(
                        "Não existe uma disciplina para esse assunto",
                        "DISCIPLINA_INEXISTENTE"
                );
            }

            if (nomeRestricao.contains("ck_assunto_d41_ordem_obrigatoria")) {
                return new ValidationException(
                        "O campo ordem é de preenchimento obrigatório",
                        "ORDEM_OBRIGATORIA",
                        "ordem"
                );
            }
        }

        throw e;
    }
}
