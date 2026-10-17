package br.com.estudos.segmento;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** O motivo de o acervo não ensinar um assunto, para o detalhe dele (D-65, docs/SPRINT-19-MATERIAL-AUTOMATICO.md §5). */
@RestController
@Tag(name = "Segmentos")
public class LacunaController {

    public record LacunaResponse(String motivo, Instant registradaEm) {}

    private final MaterialService materialService;

    public LacunaController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/api/assuntos/{assuntoId}/lacuna")
    @Operation(summary = "Lacuna de material do assunto", description = "404 quando não há lacuna registrada.")
    public ResponseEntity<LacunaResponse> lacuna(@PathVariable Long assuntoId) {
        return materialService.lacunaDe(assuntoId)
            .map(l -> ResponseEntity.ok(new LacunaResponse(l.getMotivo(), l.getRegistradaEm())))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
