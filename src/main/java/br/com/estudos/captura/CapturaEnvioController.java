package br.com.estudos.captura;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/** ADR-063: as duas rotas da chave de envio. Só gravam; a resposta nunca traz dado do SGE além do nome do assunto achado. */
@RestController
@Tag(name = "Captura (envio)", description = "O userscript do QConcursos envia aqui, com a chave de envio (ADR-063)")
public class CapturaEnvioController {

    private final CapturaService captura;

    public CapturaEnvioController(CapturaService captura) {
        this.captura = captura;
    }

    @PostMapping("/captura/qconcursos/respostas")
    @Operation(summary = "Uma questão respondida (D-166)")
    public CapturaService.Recebida questao(@RequestBody CapturaService.Enviada q, HttpServletRequest pedido) {
        return captura.receber(chave(pedido), q);
    }

    @PostMapping("/captura/qconcursos/diagnosticos")
    @Operation(summary = "O recorte de uma questão que o userscript não entendeu (D-171)")
    public Map<String, Boolean> diagnostico(@RequestBody CapturaService.Diagnostico d, HttpServletRequest pedido) {
        captura.diagnostico(chave(pedido), d);
        return Map.of("ok", true);
    }

    private static long chave(HttpServletRequest pedido) {
        var id = pedido.getAttribute(CapturaFiltro.ATRIBUTO);
        if (id == null) throw new IllegalStateException("captura sem chave: o CapturaFiltro não rodou");
        return (Long) id;
    }
}
