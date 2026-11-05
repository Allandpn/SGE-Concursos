package br.com.estudos.captura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.acesso.OrigemDoPedido;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/** A captura do QConcursos com a sessão (04_FRONTEND §8F): as chaves, a caixa, o lote, os pares, os diagnósticos e o userscript. */
@RestController
@Tag(name = "Captura", description = "A captura do QConcursos (01_DOMINIO §3.28)")
public class CapturaController {

    public record NovaChave(String nome) {}

    public record RegistrarCaixa(Long assuntoId, Short previsaoPercentual, Integer tempoMinutos) {}

    public record Par(String nomeQc, Long assuntoId) {}

    public record Descartar(Long assuntoId, String nomeQc) {}

    public record Marcar(List<Long> ids, Long sessaoId) {}

    private final CapturaChaveService chaves;
    private final CapturaService captura;

    public CapturaController(CapturaChaveService chaves, CapturaService captura) {
        this.chaves = chaves;
        this.captura = captura;
    }

    @GetMapping("/api/captura/chaves")
    public List<CapturaChaveService.Chave> chaves() {
        return chaves.listar();
    }

    @PostMapping("/api/captura/chaves")
    @Operation(summary = "Gera a chave de envio deste navegador; ela só aparece nesta resposta (D-170)")
    public CapturaChaveService.Criada gerar(@RequestBody NovaChave corpo) {
        return chaves.gerar(corpo.nome());
    }

    @PostMapping("/api/captura/chaves/{id}/revogar")
    public ResponseEntity<Void> revogar(@PathVariable long id) {
        chaves.revogar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/captura/caixa")
    @Operation(summary = "A caixa em Hoje: as capturadas não registradas, por assunto, sem o acerto (D-169)")
    public List<CapturaService.GrupoCaixa> caixa() {
        return captura.caixa();
    }

    @PostMapping("/api/captura/caixa/registrar")
    public CapturaService.Registrado registrar(@RequestBody RegistrarCaixa corpo) {
        if (corpo.assuntoId() == null) throw new br.com.estudos.shared.exception.ValidationException("Falta o assunto.", "CAIXA_SEM_ASSUNTO", "assuntoId");
        return captura.registrarCaixa(corpo.assuntoId(), corpo.previsaoPercentual(), corpo.tempoMinutos());
    }

    @PostMapping("/api/captura/caixa/descartar")
    public Map<String, Integer> descartar(@RequestBody Descartar corpo) {
        return Map.of("descartadas", captura.descartar(corpo.assuntoId(), corpo.nomeQc()));
    }

    @PostMapping("/api/captura/pares")
    @Operation(summary = "Escolhe o assunto de um nome do QConcursos e aprende o par (D-167)")
    public Map<String, Integer> parear(@RequestBody Par corpo) {
        if (corpo.assuntoId() == null) throw new br.com.estudos.shared.exception.ValidationException("Falta o assunto.", "PAR_SEM_ASSUNTO", "assuntoId");
        return Map.of("movidas", captura.parear(corpo.nomeQc(), corpo.assuntoId()));
    }

    @GetMapping("/api/captura/desde")
    @Operation(summary = "As capturadas do assunto desde a saída pelo atalho, para o lote (D-168)")
    public CapturaService.Desde desde(@RequestParam long assuntoId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde) {
        return captura.desde(assuntoId, desde);
    }

    @PostMapping("/api/captura/marcar")
    public Map<String, Integer> marcar(@RequestBody Marcar corpo) {
        if (corpo.sessaoId() == null) throw new br.com.estudos.shared.exception.ValidationException("Falta a sessão.", "MARCAR_SEM_SESSAO", "sessaoId");
        return Map.of("marcadas", captura.marcar(corpo.ids(), corpo.sessaoId()));
    }

    @GetMapping("/api/captura/diagnosticos")
    public List<CapturaService.DiagnosticoRecebido> diagnosticos() {
        return captura.diagnosticos();
    }

    @DeleteMapping("/api/captura/diagnosticos/{id}")
    public ResponseEntity<Void> apagar(@PathVariable long id) {
        captura.apagarDiagnostico(id);
        return ResponseEntity.noContent().build();
    }

    /** ADR-063: o userscript, servido pelo SGE (com a sessão), já com o endereço dele. */
    @GetMapping("/captura/eagora-qconcursos.user.js")
    public ResponseEntity<String> userscript(HttpServletRequest pedido) throws IOException {
        var origem = OrigemDoPedido.origem(pedido);
        var host = java.net.URI.create(origem).getHost();
        var texto = new String(new ClassPathResource("captura/eagora-qconcursos.user.js").getInputStream().readAllBytes(), StandardCharsets.UTF_8)
            .replace("__SGE__", origem).replace("__HOST__", host);
        return ResponseEntity.ok().contentType(new MediaType("text", "javascript", StandardCharsets.UTF_8))
            .header(HttpHeaders.CACHE_CONTROL, "no-store").body(texto);
    }
}
