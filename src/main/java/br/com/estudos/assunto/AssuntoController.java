package br.com.estudos.assunto;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Endpoints de assunto (docs/SPRINT-2-CADASTRO.md §4). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/assuntos")
public class AssuntoController {

    private final AssuntoService assuntoService;

    public AssuntoController(AssuntoService assuntoService) {
        this.assuntoService = assuntoService;
    }

    @GetMapping
    public List<AssuntoResponse> listar(@RequestParam Long disciplinaId) {
        return assuntoService.listar(disciplinaId).stream().map(AssuntoMapper::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<AssuntoResponse> criar(@RequestBody AssuntoRequest request, UriComponentsBuilder uriBuilder) {
        var assunto = assuntoService.criar(request);
        var uri = uriBuilder.path("/api/assuntos/{id}").buildAndExpand(assunto.getId()).toUri();
        return ResponseEntity.created(uri).body(AssuntoMapper.toResponse(assunto));
    }

    @PatchMapping("/{id}")
    public AssuntoResponse atualizar(@PathVariable Long id, @RequestBody AssuntoRequest request) {
        return AssuntoMapper.toResponse(assuntoService.atualizar(id, request));
    }

    @PostMapping("/{id}/arquivar")
    public ResponseEntity<Void> arquivar(@PathVariable Long id) {
        assuntoService.arquivar(id);
        return ResponseEntity.noContent().build();
    }
}
