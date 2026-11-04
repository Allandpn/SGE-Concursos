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
     * `assuntoId` também é validado eager, mas por motivo diferente: mesmo
     * coberto por D-46 (`fk_erro_d46_assunto`), `null` nunca chegaria ao
     * banco — `getReferenceById(null)` lança antes de qualquer INSERT, então
     * não há violação de restrição pra `traduzirViolacaoDeIntegridade`
     * traduzir.
     */
    @Transactional
    public Erro registrar(ErroRequest request) {
        if (request.assuntoId() == null) {
            throw new ValidationException("Assunto é obrigatório.", "ASSUNTO_OBRIGATORIO", "assuntoId");
        }
        if (request.descricao() == null || request.descricao().isBlank()) {
            throw new ValidationException("Descrição é obrigatória.", "DESCRICAO_OBRIGATORIA", "descricao");
        }
        if (request.causa() == null) {
            throw new ValidationException("Causa é obrigatória.", "CAUSA_OBRIGATORIA", "causa");
        }
        if (request.confianca() == null) {
            throw new ValidationException("Confiança é obrigatória.", "CONFIANCA_OBRIGATORIA", "confianca");
        }

        var questao = questaoExterna(request.questaoExterna());
        if (request.sessaoId() != null) {
            limiteDoLote(request.sessaoId());
        }

        var erro = new Erro();
        erro.setAssunto(assuntoRepository.getReferenceById(request.assuntoId()));
        if (request.sessaoId() != null) {
            erro.setSessao(sessaoRepository.getReferenceById(request.sessaoId()));
        }
        erro.setQuestaoExterna(questao);
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

    private static final java.util.regex.Pattern QUESTAO = java.util.regex.Pattern.compile("Q?(\\d{4,10})");

    /** D-156: "q2318890", " Q2318890 " ou "2318890" viram "Q2318890"; outro formato é recusado. */
    static String questaoExterna(String bruto) {
        if (bruto == null || bruto.isBlank()) return null;
        var m = QUESTAO.matcher(bruto.trim().toUpperCase(java.util.Locale.ROOT));
        if (!m.matches()) {
            throw new ValidationException("O número da questão é um Q seguido de 4 a 10 dígitos (ex.: Q2318890).", "QUESTAO_INVALIDA",
                "questaoExterna");
        }
        return "Q" + m.group(1);
    }

    /** D-156: numa sessão de questões, no máximo total − corretas erros ligados a ela. */
    private void limiteDoLote(Long sessaoId) {
        sessaoRepository.findById(sessaoId).ifPresent(s -> {
            if (s.getTipo() != br.com.estudos.shared.enums.TipoSessao.QUESTOES || s.getQuestoesTotal() == null) return;
            var erradas = s.getQuestoesTotal() - (s.getQuestoesCorretas() == null ? 0 : s.getQuestoesCorretas());
            if (erroRepository.countBySessaoId(sessaoId) >= erradas) {
                throw new br.com.estudos.shared.exception.BusinessRuleException(
                    "Este lote teve " + erradas + (erradas == 1 ? " errada" : " erradas") + "; não cabem mais erros nele.",
                    "ERROS_ACIMA_DO_LOTE");
            }
        });
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
