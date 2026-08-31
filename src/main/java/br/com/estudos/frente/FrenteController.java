package br.com.estudos.frente;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.assunto.AssuntoMapper;
import br.com.estudos.assunto.AssuntoResponse;

/** Endpoints de frente/backlog/consolidado (docs/SPRINT-5-FRENTE.md §3). Só leitura — nada aqui grava. */
@RestController
public class FrenteController {

    private final FrenteService frenteService;

    public FrenteController(FrenteService frenteService) {
        this.frenteService = frenteService;
    }

    @GetMapping("/api/assuntos/{id}/fase")
    public AssuntoFaseResponse fase(@PathVariable Long id) {
        return new AssuntoFaseResponse(id, frenteService.fase(id));
    }

    @GetMapping("/api/frente")
    public FrenteResumoResponse resumo() {
        return frenteService.resumo();
    }

    @GetMapping("/api/frente/proxima-vaga")
    public ResponseEntity<AssuntoResponse> proximaVaga(@RequestParam Long disciplinaId) {
        return frenteService.proximaVaga(disciplinaId)
            .map(assunto -> ResponseEntity.ok(AssuntoMapper.toResponse(assunto)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
