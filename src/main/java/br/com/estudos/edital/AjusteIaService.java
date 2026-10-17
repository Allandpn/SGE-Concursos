package br.com.estudos.edital;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.exception.IndisponivelException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Ajustes → IA (ADR-044, docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3). A chave é
 * gravada e nunca devolvida: a resposta só mostra os quatro últimos caracteres.
 */
@Service
public class AjusteIaService {

    private static final int CHAVE_MINIMA = 8;

    private final AjusteIaRepository repository;
    private final ConfiguracaoIa configuracao;
    private final ClienteOpenRouter cliente;
    private final Clock clock;

    public AjusteIaService(AjusteIaRepository repository, ConfiguracaoIa configuracao, ClienteOpenRouter cliente, Clock clock) {
        this.repository = repository;
        this.configuracao = configuracao;
        this.cliente = cliente;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AjusteIaResponse obter() {
        var chave = configuracao.chave();
        var final4 = chave == null || chave.length() < 4 ? null : chave.substring(chave.length() - 4);
        return new AjusteIaResponse(configuracao.disponivel(), configuracao.modelo(), final4, configuracao.origemDaChave());
    }

    /** Campo ausente ou em branco mantém o que está; só grava o que veio preenchido. */
    @Transactional
    public AjusteIaResponse atualizar(AjusteIaRequest pedido) {
        var chave = limpo(pedido.chave());
        var modelo = limpo(pedido.modelo());
        if (chave != null && chave.length() < CHAVE_MINIMA) {
            throw new ValidationException("Essa chave é curta demais para ser do OpenRouter.", "CHAVE_INVALIDA", "chave");
        }
        if (modelo != null && modelo.length() > 150) {
            throw new ValidationException("Identificador de modelo longo demais.", "MODELO_INVALIDO", "modelo");
        }
        var ajuste = repository.findById(AjusteIa.UNICA).orElseGet(() -> new AjusteIa(Instant.now(clock)));
        if (chave != null) ajuste.setChave(chave);
        if (modelo != null) ajuste.setModelo(modelo);
        ajuste.setAtualizadoEm(Instant.now(clock));
        repository.saveAndFlush(ajuste);
        return obter();
    }

    public List<ModeloIa> modelos() {
        try {
            return cliente.listarModelos();
        } catch (FalhaIaException e) {
            throw new IndisponivelException(e.getMessage(), "IA_INDISPONIVEL");
        }
    }

    private static String limpo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }
}
