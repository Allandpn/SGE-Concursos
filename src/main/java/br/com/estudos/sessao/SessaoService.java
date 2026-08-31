package br.com.estudos.sessao;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.revisao.RevisaoService;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;
import br.com.estudos.shared.parametro.ParametroRepository;

@Service
public class SessaoService {

    private final SessaoRepository sessaoRepository;
    private final AssuntoRepository assuntoRepository;
    private final ParametroRepository parametroRepository;
    private final RevisaoService revisaoService;
    private final SessaoService self;

    public SessaoService(
            SessaoRepository sessaoRepository,
            AssuntoRepository assuntoRepository,
            ParametroRepository parametroRepository,
            RevisaoService revisaoService,
            @Lazy SessaoService self) {
        this.sessaoRepository = sessaoRepository;
        this.assuntoRepository = assuntoRepository;
        this.parametroRepository = parametroRepository;
        this.revisaoService = revisaoService;
        this.self = self;
    }

    /**
     * Registra uma sessão. Não pré-verifica assunto existente nem tentativa
     * duplicada — grava direto e traduz a exceção do driver pelo nome da
     * restrição, mesmo princípio de D-05 (ADR-031). D-45 é o caso especial:
     * reenvio da mesma tentativa devolve sucesso, não erro.
     * docs/SPRINT-3-SESSAO.md §3.
     *
     * Não é @Transactional: precisa poder tentar {@link #gravar} e, se cair em
     * D-45, chamar {@link #buscarPorTentativa} numa transação NOVA. A sessão do
     * Hibernate fica inutilizável para qualquer operação depois de um flush que
     * falhou (AssertionFailure: "has a null identifier") — reaproveitar a mesma
     * transação pra buscar em seguida quebra. `self` (injeção @Lazy do próprio
     * bean) é necessário porque `this.gravar(...)` direto pula o proxy do
     * Spring e o @Transactional dos dois métodos abaixo não vale nada
     * (autoinvocação — suspeito de sempre da mentoria).
     */
    public Sessao registrar(SessaoRequest request) {
        try {
            return self.gravar(request);
        } catch (DataIntegrityViolationException e) {
            var nomeRestricao = e.getMostSpecificCause().getMessage();

            // D-45 (ADR-031): reenvio da mesma tentativa é sucesso, não erro —
            // devolve a sessão já gravada em vez de propagar. GlobalExceptionHandler
            // nunca é acionado neste caminho.
            if (nomeRestricao != null && nomeRestricao.contains("ux_sessao_d45_tentativa_unica")) {
                return self.buscarPorTentativa(request.tentativaId());
            }

            throw traduzirViolacaoDeIntegridade(nomeRestricao, e);
        }
    }

    @Transactional
    public Sessao gravar(SessaoRequest request) {
        var sessao = new Sessao();
        sessao.setAssunto(assuntoRepository.getReferenceById(request.assuntoId()));
        sessao.setTipo(request.tipo());
        sessao.setData(request.data());
        sessao.setTempoMinutos(request.tempoMinutos());
        sessao.setQuestoesCorretas(request.questoesCorretas());
        sessao.setQuestoesTotal(request.questoesTotal());
        sessao.setFormato(request.formato());
        sessao.setPrevisaoPercentual(request.previsaoPercentual());
        sessao.setPrevisaoReconstrucao(request.previsaoReconstrucao());
        sessao.setTentativaId(request.tentativaId());
        sessao.setProximaSessaoData(request.proximaSessaoData());
        sessao.setProximaSessaoDescricao(request.proximaSessaoDescricao());
        sessao.setAtivo(true);
        sessao.setResultado(calcularResultado(request));

        var salva = sessaoRepository.save(sessao);
        // Mesma transação: revisão é efeito de sessão, não passo separado
        // (docs/SPRINT-4-ESCADA.md §3).
        revisaoService.processarSessao(salva);
        return salva;
    }

    @Transactional(readOnly = true)
    public Sessao buscarPorTentativa(UUID tentativaId) {
        return sessaoRepository.findByTentativaId(tentativaId)
            .orElseThrow(); // não pode faltar: a unicidade acabou de confirmar que existe
    }

    /** Busca uma sessão pelo id. Lança se não existir. */
    @Transactional(readOnly = true)
    public Sessao buscar(Long id) {
        return sessaoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Sessão não encontrada.", "SESSAO_INEXISTENTE"));
    }

    /** Lista as sessões de um assunto. */
    @Transactional(readOnly = true)
    public List<Sessao> listar(Long assuntoId) {
        return sessaoRepository.findByAssuntoId(assuntoId);
    }

    /**
     * `QUESTOES`/`FLASHCARDS` apuram por percentual contra limiar parametrizado
     * (01_DOMINIO §4.1/§4.2) — o `resultado` do request é ignorado nesses dois
     * casos. `RECUPERACAO` é declarado pelo cliente. `ESTUDO` nunca tem
     * resultado (D-02). docs/SPRINT-3-SESSAO.md §3.2.
     */
    private ResultadoSessao calcularResultado(SessaoRequest request) {
        return switch (request.tipo()) {
            case ESTUDO -> {
                if (request.resultado() != null) {
                    throw new ValidationException(
                        "Sessão ESTUDO não tem resultado.", "ESTUDO_SEM_RESULTADO", "resultado");
                }
                yield null;
            }
            case RECUPERACAO -> {
                if (request.resultado() == null) {
                    throw new ValidationException(
                        "Resultado é obrigatório para RECUPERACAO.", "RESULTADO_OBRIGATORIO", "resultado");
                }
                yield request.resultado();
            }
            case QUESTOES -> {
                exigirContagemDeQuestoes(request);
                // formato ausente aqui vira FORMATO_OBRIGATORIO (D-36) só quando o save() rejeitar —
                // sem isso o cálculo não teria limiar pra usar.
                yield request.formato() == null ? null : calcularPorLimiar(request, limiaresPorFormato(request.formato()));
            }
            case FLASHCARDS -> {
                exigirContagemDeQuestoes(request);
                yield calcularPorLimiar(request, limiaresFlashcards());
            }
        };
    }

    private void exigirContagemDeQuestoes(SessaoRequest request) {
        if (request.questoesCorretas() == null || request.questoesTotal() == null) {
            throw new ValidationException(
                "questoesCorretas e questoesTotal são obrigatórios para " + request.tipo() + ".",
                "QUESTOES_OBRIGATORIAS", "questoesTotal");
        }
    }

    private ResultadoSessao calcularPorLimiar(SessaoRequest request, Limiares limiares) {
        var percentual = request.questoesCorretas() * 100.0 / request.questoesTotal();
        if (percentual >= limiares.sucesso()) return ResultadoSessao.SUCESSO;
        if (percentual >= limiares.parcial()) return ResultadoSessao.PARCIAL;
        return ResultadoSessao.FALHA;
    }

    private Limiares limiaresPorFormato(FormatoBanca formato) {
        return switch (formato) {
            case MULTIPLA_ESCOLHA ->
                new Limiares(valorParametro("limiar_sucesso_multipla_escolha"), valorParametro("limiar_parcial_multipla_escolha"));
            case CERTO_ERRADO ->
                new Limiares(valorParametro("limiar_sucesso_certo_errado"), valorParametro("limiar_parcial_certo_errado"));
        };
    }

    private Limiares limiaresFlashcards() {
        return new Limiares(valorParametro("limiar_sucesso_flashcards"), valorParametro("limiar_parcial_flashcards"));
    }

    private double valorParametro(String chave) {
        return parametroRepository.findById(chave)
            .map(p -> Double.parseDouble(p.getValor()))
            .orElseThrow(() -> new IllegalStateException("Parâmetro obrigatório ausente: " + chave));
    }

    private record Limiares(double sucesso, double parcial) {}

    /**
     * Traduz a exceção do driver pelo nome da restrição violada (§3.1 do doc
     * técnico) — as quatro que viram erro de domínio. D-45 já foi tratada
     * antes de chegar aqui (não é erro). Restrição não mapeada relança crua:
     * "higiene de dado" sem regra D-xx numerada não merece codigo próprio, e
     * esconder atrás de um catch genérico é o que 09_CODE_STYLE §3.2 proíbe.
     */
    private RuntimeException traduzirViolacaoDeIntegridade(String nomeRestricao, DataIntegrityViolationException e) {
        if (nomeRestricao == null) throw e;

        if (nomeRestricao.contains("fk_sessao_d01_assunto")) {
            return new NotFoundException("Não existe um assunto para essa sessão.", "ASSUNTO_INEXISTENTE");
        }
        if (nomeRestricao.contains("ck_sessao_d02_estudo_sem_resultado")) {
            return new ValidationException("Sessão ESTUDO não tem resultado.", "ESTUDO_SEM_RESULTADO", "resultado");
        }
        if (nomeRestricao.contains("ck_sessao_d04a_previsao_por_tipo")) {
            return new ValidationException("Previsão inválida para o tipo de sessão.", "PREVISAO_INVALIDA", null);
        }
        if (nomeRestricao.contains("ck_sessao_d36_questoes_tem_formato")) {
            return new ValidationException("Formato é obrigatório para QUESTOES.", "FORMATO_OBRIGATORIO", "formato");
        }

        throw e;
    }
}
