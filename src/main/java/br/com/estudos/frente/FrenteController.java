package br.com.estudos.frente;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.assunto.AssuntoMapper;
import br.com.estudos.assunto.AssuntoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints de frente/backlog/consolidado (docs/SPRINT-5-FRENTE.md §3). Só leitura — nada aqui grava. */
@RestController
@Tag(name = "Frente de estudo", description = "Fase do assunto, resumo e próxima vaga do backlog — tudo derivado, nada persistido (ADR-033)")
public class FrenteController {

    private final FrenteService frenteService;

    public FrenteController(FrenteService frenteService) {
        this.frenteService = frenteService;
    }

    @GetMapping("/api/assuntos/{id}/fase")
    @Operation(summary = "Consulta a fase de um assunto", description = "Backlog, em estudo, em escada, consolidado ou em manutenção — derivada, nunca guardada (D-16).")
    public AssuntoFaseResponse fase(@PathVariable Long id) {
        return new AssuntoFaseResponse(id, frenteService.fase(id));
    }

    @GetMapping("/api/frente")
    @Operation(summary = "Resumo da frente de estudo", description = "Frente atual, teto, backlog, consolidados e represamento.")
    public FrenteResumoResponse resumo() {
        return frenteService.resumo();
    }

    @GetMapping("/api/frente/proxima-vaga")
    @Operation(summary = "Sugere o próximo assunto do backlog de uma disciplina", description = "O de menor ordem ainda não iniciado (D-41) — recomendação, nunca catraca.")
    public ResponseEntity<AssuntoResponse> proximaVaga(@RequestParam Long disciplinaId) {
        return frenteService.proximaVaga(disciplinaId)
            .map(assunto -> ResponseEntity.ok(AssuntoMapper.toResponse(assunto)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
