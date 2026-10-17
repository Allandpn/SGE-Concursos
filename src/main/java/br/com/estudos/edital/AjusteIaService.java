package br.com.estudos.edital;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.IndisponivelException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Ajustes → IA (ADR-044, ADR-045, docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3 e §3A).
 * Token e chave são gravados e nunca devolvidos: a resposta só mostra os quatro
 * últimos caracteres. A troca de provedor é sempre pedido do usuário.
 */
@Service
public class AjusteIaService {

    private static final int SEGREDO_MINIMO = 8;

    private final AjusteIaRepository repository;
    private final ConfiguracaoIa configuracao;
    private final ClienteOpenRouter openRouter;
    private final Clock clock;

    public AjusteIaService(AjusteIaRepository repository, ConfiguracaoIa configuracao, ClienteOpenRouter openRouter, Clock clock) {
        this.repository = repository;
        this.configuracao = configuracao;
        this.openRouter = openRouter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AjusteIaResponse obter() {
        var falha = configuracao.falhaClaude()
            .map(f -> new AjusteIaResponse.Falha(f.motivo(), f.em()))
            .orElse(null);
        return new AjusteIaResponse(
            configuracao.provedor(),
            configuracao.disponivel(),
            new AjusteIaResponse.Claude(configuracao.claudeConfigurado(), final4(configuracao.claudeToken()),
                configuracao.claudeModelo(), falha),
            new AjusteIaResponse.OpenRouter(configuracao.openrouterConfigurado(), final4(configuracao.chave()),
                configuracao.modelo(), configuracao.origemDaChave()));
    }

    /** Campo ausente ou em branco mantém o que está; só grava o que veio preenchido. */
    @Transactional
    public AjusteIaResponse atualizar(AjusteIaRequest pedido) {
        var token = limpo(pedido.claudeToken());
        var claudeModelo = limpo(pedido.claudeModelo());
        var chave = limpo(pedido.chave());
        var modelo = limpo(pedido.modelo());
        if (token != null && token.length() < SEGREDO_MINIMO) {
            throw new ValidationException("Esse token é curto demais para ser da assinatura do Claude.", "TOKEN_INVALIDO", "claudeToken");
        }
        if (claudeModelo != null && !ConfiguracaoIa.MODELOS_CLAUDE.contains(claudeModelo)) {
            throw new ValidationException("Modelo do Claude: opus, sonnet ou haiku.", "MODELO_INVALIDO", "claudeModelo");
        }
        if (chave != null && chave.length() < SEGREDO_MINIMO) {
            throw new ValidationException("Essa chave é curta demais para ser do OpenRouter.", "CHAVE_INVALIDA", "chave");
        }
        if (modelo != null && modelo.length() > 150) {
            throw new ValidationException("Identificador de modelo longo demais.", "MODELO_INVALIDO", "modelo");
        }

        var ajuste = repository.findById(AjusteIa.UNICA).orElseGet(() -> new AjusteIa(Instant.now(clock)));
        if (token != null) ajuste.setClaudeToken(token);
        if (claudeModelo != null) ajuste.setClaudeModelo(claudeModelo);
        if (chave != null) ajuste.setChave(chave);
        if (modelo != null) ajuste.setModelo(modelo);
        if (pedido.provedor() != null) {
            ajuste.setProvedor(pedido.provedor());
            if (pedido.provedor() == ProvedorIa.CLAUDE) {
                // "Tentar o Claude de novo": o aviso some; se ainda falhar, volta na próxima chamada.
                ajuste.setClaudeFalhaMotivo(null);
                ajuste.setClaudeFalhaEm(null);
            }
        }
        ajuste.setAtualizadoEm(Instant.now(clock));
        repository.saveAndFlush(ajuste);

        // Depois de gravar: escolher um provedor sem credencial deixaria a IA parada sem aviso útil.
        if (pedido.provedor() == ProvedorIa.OPENROUTER && !configuracao.openrouterConfigurado()) {
            throw new ValidationException("Cadastre a chave do OpenRouter antes de usá-lo.", "OPENROUTER_SEM_CHAVE", "provedor");
        }
        if (pedido.provedor() == ProvedorIa.CLAUDE && !configuracao.claudeConfigurado()) {
            throw new ValidationException("Cadastre o token da assinatura do Claude antes de usá-lo.", "CLAUDE_SEM_TOKEN", "provedor");
        }
        return obter();
    }

    public List<ModeloIa> modelos() {
        try {
            return openRouter.listarModelos();
        } catch (FalhaIaException e) {
            throw new IndisponivelException(e.getMessage(), "IA_INDISPONIVEL");
        }
    }

    private static String final4(String segredo) {
        return segredo == null || segredo.length() < 4 ? null : segredo.substring(segredo.length() - 4);
    }

    private static String limpo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
