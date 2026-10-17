package br.com.estudos.segmento;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.estudos.edital.ConfiguracaoIa;
import br.com.estudos.edital.FalhaIaException;
import br.com.estudos.edital.ServicoIa;
import br.com.estudos.shared.exception.IndisponivelException;

/**
 * Rotas do sistema de planejamento rodando no mesmo servidor (ADR-044,
 * docs/SPRINT-19-MATERIAL-AUTOMATICO.md §4). O token é checado antes, por
 * TokenInternoFilter.
 */
@RestController
@RequestMapping("/internal")
@Tag(name = "Interno", description = "Só para o planejamento no mesmo servidor; exige X-Internal-Token (ADR-044)")
public class InternoController {

    public record CompletarRequest(String sistema, String usuario) {}

    public record CompletarResponse(String conteudo, String modelo) {}

    public record LacunaRequest(String chaveExterna, String motivo) {}

    private final ServicoIa cliente;
    private final ConfiguracaoIa configuracao;
    private final MaterialService materialService;

    public InternoController(ServicoIa cliente, ConfiguracaoIa configuracao, MaterialService materialService) {
        this.cliente = cliente;
        this.configuracao = configuracao;
        this.materialService = materialService;
    }

    @PostMapping("/ia/completar")
    @Operation(summary = "Proxy de IA", description = "Usa o provedor em uso em Ajustes → IA (ADR-045); o planejamento nunca guarda credencial.")
    public CompletarResponse completar(@RequestBody CompletarRequest pedido) {
        if (!configuracao.disponivel()) {
            throw new IndisponivelException("Cadastre a IA em Ajustes → IA.", "IA_INDISPONIVEL");
        }
        try {
            return new CompletarResponse(cliente.completar(pedido.sistema(), pedido.usuario()), configuracao.modeloEmUso());
        } catch (FalhaIaException e) {
            // ADR-045: assinatura parada é decisão do usuário — o worker espera, não marca lacuna.
            throw new IndisponivelException(e.getMessage(), e.assinaturaParou() ? "IA_PAUSADA" : "IA_FALHOU");
        }
    }

    @PostMapping("/material/lacunas")
    @Operation(summary = "O acervo não ensina este assunto (D-65)")
    public ResponseEntity<Void> registrarLacuna(@RequestBody LacunaRequest pedido) {
        materialService.registrarLacuna(pedido.chaveExterna(), pedido.motivo());
        return ResponseEntity.noContent().build();
    }
}
