package br.com.estudos.ensaio;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import br.com.estudos.frente.FrenteService;
import br.com.estudos.revisao.RevisaoRepository;
import br.com.estudos.sessao.SessaoRepository;
import br.com.estudos.sessao.SessaoRequest;
import br.com.estudos.sessao.SessaoService;
import br.com.estudos.shared.enums.FaseAssunto;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.SituacaoRevisao;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;
import br.com.estudos.shared.parametro.ParametroRepository;

/**
 * O ensaio (01_DOMINIO §3.25, D-159; ADR-059): o registro real numa transação que é sempre desfeita. Nenhuma regra da escada é
 * copiada aqui — só se lê o que o registro deixaria.
 */
@Service
public class EnsaioService {

    public enum Efeito { CUMPRE, ABRE_ESCADA, SEM_MUDANCA }

    public enum Motivo { FORA_DA_JANELA, LOTE_PEQUENO, NUNCA_LIDO, LEITURA }

    public record Proxima(int nivel, LocalDate dataPrevista) {}

    public record Ensaio(ResultadoSessao resultado, Integer percentual, Efeito efeito, Motivo motivo, Proxima proxima, boolean consolidaria) {}

    private final SessaoService sessoes;
    private final SessaoRepository sessaoRepository;
    private final RevisaoRepository revisoes;
    private final FrenteService frente;
    private final ParametroRepository parametros;

    public EnsaioService(SessaoService sessoes, SessaoRepository sessaoRepository, RevisaoRepository revisoes, FrenteService frente,
            ParametroRepository parametros) {
        this.sessoes = sessoes;
        this.sessaoRepository = sessaoRepository;
        this.revisoes = revisoes;
        this.frente = frente;
        this.parametros = parametros;
    }

    @Transactional
    public Ensaio ensaiar(SessaoRequest pedido) {
        // Desfeito aconteça o que acontecer: marcado antes de qualquer escrita (ADR-059).
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        if (pedido == null || pedido.assuntoId() == null) {
            throw new ValidationException("Assunto é obrigatório.", "ASSUNTO_OBRIGATORIO", "assuntoId");
        }
        var assuntoId = pedido.assuntoId();
        var antes = revisoes.findByAssuntoIdAndSituacao(assuntoId, SituacaoRevisao.PENDENTE);
        var antesId = antes.map(r -> r.getId()).orElse(null);
        var faseAntes = frente.fase(assuntoId);
        var nuncaLido = !revisoes.existsByAssuntoId(assuntoId) && !sessaoRepository.assuntoJaLido(assuntoId);

        // Tentativa nova (D-45 intocada) e sem focos: o ensaio não consome o Pomodoro.
        var request = new SessaoRequest(assuntoId, pedido.tipo(), pedido.data() == null ? LocalDate.now() : pedido.data(),
            pedido.tempoMinutos(), pedido.questoesCorretas(), pedido.questoesTotal(), pedido.formato(), pedido.previsaoPercentual(),
            pedido.previsaoReconstrucao(), pedido.resultado(), UUID.randomUUID(), pedido.segmentoId(), List.of(), pedido.conferencia(),
            pedido.notaDiscursiva());
        br.com.estudos.sessao.Sessao sessao;
        try {
            sessao = sessoes.gravar(request);
        } catch (DataIntegrityViolationException e) {
            throw new NotFoundException("Não foi possível ensaiar: o assunto ou o bloco não existe.", "ENSAIO_INVALIDO");
        }

        var depois = revisoes.findByAssuntoIdAndSituacao(assuntoId, SituacaoRevisao.PENDENTE);
        var proxima = depois.map(r -> new Proxima(r.getNivel(), r.getDataPrevista())).orElse(null);
        Efeito efeito;
        Motivo motivo = null;
        if (antesId != null && depois.map(r -> !r.getId().equals(antesId)).orElse(true)) {
            efeito = Efeito.CUMPRE;
        } else if (antesId == null && depois.isPresent()) {
            efeito = Efeito.ABRE_ESCADA;
        } else {
            efeito = Efeito.SEM_MUDANCA;
            var contagem = sessao.getTipo() == TipoSessao.QUESTOES || sessao.getTipo() == TipoSessao.FLASHCARDS;
            if (contagem && sessao.getQuestoesTotal() != null && sessao.getQuestoesTotal() < loteMinimo()) motivo = Motivo.LOTE_PEQUENO;
            else if (sessao.getTipo() == TipoSessao.QUESTOES && nuncaLido) motivo = Motivo.NUNCA_LIDO;
            else if (sessao.getTipo() == TipoSessao.ESTUDO) motivo = Motivo.LEITURA;
            else if (antesId != null) motivo = Motivo.FORA_DA_JANELA;
            if (antesId == null) proxima = null;
        }
        var consolidaria = faseAntes != FaseAssunto.CONSOLIDADO && frente.fase(assuntoId) == FaseAssunto.CONSOLIDADO;
        Integer percentual = sessao.getQuestoesTotal() != null && sessao.getQuestoesTotal() > 0 && sessao.getQuestoesCorretas() != null
            ? Math.round(sessao.getQuestoesCorretas() * 100f / sessao.getQuestoesTotal()) : null;
        return new Ensaio(sessao.getResultado(), percentual, efeito, motivo, proxima, consolidaria);
    }

    private long loteMinimo() {
        return parametros.findById("lote_minimo_questoes").map(p -> Long.parseLong(p.getValor())).orElse(0L);
    }
}
