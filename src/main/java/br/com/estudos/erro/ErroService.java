package br.com.estudos.erro;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.sessao.SessaoRepository;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Registrar e consultar erro (docs/SPRINT-7-METRICAS.md §3). Sem
 * atualizar/resolver nesta sprint (§0 do doc técnico) — erro nasce aberto.
 */
@Service
public class ErroService {

    private final ErroRepository erroRepository;
    private final AssuntoRepository assuntoRepository;
    private final SessaoRepository sessaoRepository;

    public ErroService(
            ErroRepository erroRepository,
            AssuntoRepository assuntoRepository,
            SessaoRepository sessaoRepository) {
        this.erroRepository = erroRepository;
        this.assuntoRepository = assuntoRepository;
        this.sessaoRepository = sessaoRepository;
    }

    /**
     * Não pré-verifica assunto/sessão existentes — grava direto e traduz a
     * exceção do driver pelo nome da restrição, mesmo princípio de D-05
     * (unicidade/integridade na persistência, nunca checagem prévia no
     * serviço). `descricao`/`causa`/`confianca` são validados eager, não
     * traduzidos do banco — são `NOT NULL` sem `D-xx`, e um valor omitido é
     * erro plausível do cliente, não "higiene de dado" que valha relançar
     * cru (mesmo padrão de `SessaoService.exigirContagemDeQuestoes`).
     */
    @Transactional
    public Erro registrar(ErroRequest request) {
        if (request.descricao() == null || request.descricao().isBlank()) {
            throw new ValidationException("Descrição é obrigatória.", "DESCRICAO_OBRIGATORIA", "descricao");
        }
        if (request.causa() == null) {
            throw new ValidationException("Causa é obrigatória.", "CAUSA_OBRIGATORIA", "causa");
        }
        if (request.confianca() == null) {
            throw new ValidationException("Confiança é obrigatória.", "CONFIANCA_OBRIGATORIA", "confianca");
        }

        var erro = new Erro();
        erro.setAssunto(assuntoRepository.getReferenceById(request.assuntoId()));
        if (request.sessaoId() != null) {
            erro.setSessao(sessaoRepository.getReferenceById(request.sessaoId()));
        }
        erro.setDescricao(request.descricao());
        erro.setCausa(request.causa());
        erro.setConfianca(request.confianca());
        erro.setResolvido(false);

        try {
            return erroRepository.save(erro);
        } catch (DataIntegrityViolationException e) {
            throw traduzirViolacaoDeIntegridade(e);
        }
    }

    /** Lista os erros de um assunto. */
    @Transactional(readOnly = true)
    public List<Erro> listarPorAssunto(Long assuntoId) {
        return erroRepository.findByAssuntoId(assuntoId);
    }

    private RuntimeException traduzirViolacaoDeIntegridade(DataIntegrityViolationException e) {
        var nomeRestricao = e.getMostSpecificCause().getMessage();
        if (nomeRestricao == null) throw e;

        if (nomeRestricao.contains("fk_erro_d46_assunto")) {
            return new NotFoundException("Não existe um assunto para esse erro.", "ASSUNTO_INEXISTENTE");
        }
        if (nomeRestricao.contains("fk_erro_sessao")) {
            return new NotFoundException("Não existe uma sessão para esse erro.", "SESSAO_INEXISTENTE");
        }

        throw e;
    }
}
