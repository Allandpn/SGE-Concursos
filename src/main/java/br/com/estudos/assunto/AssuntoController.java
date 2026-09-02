package br.com.estudos.assunto;

import java.util.List;

import jakarta.validation.Valid;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints de assunto (docs/SPRINT-2-CADASTRO.md §4). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/assuntos")
@Tag(name = "Assuntos", description = "A unidade de estudo, pertence a uma disciplina (docs/SPRINT-2-CADASTRO.md §4)")
public class AssuntoController {

    private final AssuntoService assuntoService;

    public AssuntoController(AssuntoService assuntoService) {
        this.assuntoService = assuntoService;
    }

    @GetMapping
    @Operation(summary = "Lista os assuntos ativos de uma disciplina")
    public List<AssuntoResponse> listar(@RequestParam Long disciplinaId) {
        return assuntoService.listar(disciplinaId).stream().map(AssuntoMapper::toResponse).toList();
    }

    @PostMapping
    @Operation(summary = "Cria um assunto")
    public ResponseEntity<AssuntoResponse> criar(@Valid @RequestBody AssuntoRequest request, UriComponentsBuilder uriBuilder) {
        var assunto = assuntoService.criar(request);
        var uri = uriBuilder.path("/api/assuntos/{id}").buildAndExpand(assunto.getId()).toUri();
        return ResponseEntity.created(uri).body(AssuntoMapper.toResponse(assunto));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualiza parcialmente um assunto", description = "Só os campos não nulos do corpo são aplicados.")
    public AssuntoResponse atualizar(@PathVariable Long id, @RequestBody AssuntoRequest request) {
        return AssuntoMapper.toResponse(assuntoService.atualizar(id, request));
    }

    @PostMapping("/{id}/arquivar")
    @Operation(summary = "Arquiva um assunto", description = "Exclusão lógica (D-18) — cancela a revisão pendente dele (D-17).")
    public ResponseEntity<Void> arquivar(@PathVariable Long id) {
        assuntoService.arquivar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reativar")
    @Operation(summary = "Reativa um assunto arquivado", description = "Simétrica de arquivar (D-49) — nunca reescreve a revisão cancelada, cria uma pendente nova no mesmo nível.")
    public ResponseEntity<Void> reativar(@PathVariable Long id) {
        assuntoService.reativar(id);
        return ResponseEntity.noContent().build();
    }
}
