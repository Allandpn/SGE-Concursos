package br.com.estudos.edital;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A única porta para a IA (ADR-045, docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3A): a
 * análise de edital e o proxy do planejamento chamam daqui. Usa o provedor em uso e
 * **nunca troca sozinho** — se a assinatura do Claude falhar, registra o motivo
 * (é o aviso das telas) e falha; continuar pelo OpenRouter é decisão do usuário.
 */
@Service
public class ServicoIa {

    private final ConfiguracaoIa configuracao;
    private final ClienteClaudeCli claude;
    private final ClienteOpenRouter openRouter;
    private final AjusteIaRepository ajustes;
    private final Clock clock;

    public ServicoIa(ConfiguracaoIa configuracao, ClienteClaudeCli claude, ClienteOpenRouter openRouter,
                     AjusteIaRepository ajustes, Clock clock) {
        this.configuracao = configuracao;
        this.claude = claude;
        this.openRouter = openRouter;
        this.ajustes = ajustes;
        this.clock = clock;
    }

    /** Fora de transação de propósito: chamada longa, e a falha precisa ser gravada mesmo com o erro subindo. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String completar(String sistema, String usuario) {
        if (configuracao.provedor() == ProvedorIa.OPENROUTER) {
            return openRouter.completar(sistema, usuario);
        }
        try {
            var resposta = claude.completar(sistema, usuario);
            if (configuracao.falhaClaude().isPresent()) {
                registrarFalha(null); // primeira resposta boa apaga o aviso
            }
            return resposta;
        } catch (FalhaIaException e) {
            registrarFalha(e.getMessage());
            throw new FalhaIaException("A assinatura do Claude parou: " + e.getMessage()
                + " Em Ajustes → IA você pode continuar pelo OpenRouter.", true);
        }
    }

    private void registrarFalha(String motivo) {
        var ajuste = ajustes.findById(AjusteIa.UNICA).orElseGet(() -> new AjusteIa(Instant.now(clock)));
        ajuste.setClaudeFalhaMotivo(motivo == null ? null : motivo.substring(0, Math.min(motivo.length(), 1000)));
        ajuste.setClaudeFalhaEm(motivo == null ? null : Instant.now(clock));
        ajustes.saveAndFlush(ajuste);
    }
}
