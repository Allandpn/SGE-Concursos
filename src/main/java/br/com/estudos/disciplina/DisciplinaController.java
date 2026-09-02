package br.com.estudos.disciplina;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints de disciplina (docs/SPRINT-2-CADASTRO.md §4). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/disciplinas")
@Tag(name = "Disciplinas", description = "Cadastro de disciplina — agrupa assunto (docs/SPRINT-2-CADASTRO.md §4)")
public class DisciplinaController {

    private final DisciplinaService disciplinaService;

    public DisciplinaController(DisciplinaService disciplinaService) {
        this.disciplinaService = disciplinaService;
    }

    @GetMapping
    @Operation(summary = "Lista as disciplinas ativas")
    public List<DisciplinaResponse> listar() {
        return disciplinaService.listar().stream().map(DisciplinaMapper::toResponse).toList();
    }

    @PostMapping
    @Operation(summary = "Cria uma disciplina")
    public ResponseEntity<DisciplinaResponse> criar(@RequestBody DisciplinaRequest request, UriComponentsBuilder uriBuilder) {
        var disciplina = disciplinaService.criar(request);
        var uri = uriBuilder.path("/api/disciplinas/{id}").buildAndExpand(disciplina.getId()).toUri();
        return ResponseEntity.created(uri).body(DisciplinaMapper.toResponse(disciplina));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualiza parcialmente uma disciplina", description = "Só os campos não nulos do corpo são aplicados.")
    public DisciplinaResponse atualizar(@PathVariable Long id, @RequestBody DisciplinaRequest request) {
        return DisciplinaMapper.toResponse(disciplinaService.atualizar(id, request));
    }

    @PostMapping("/{id}/arquivar")
    @Operation(summary = "Arquiva uma disciplina", description = "Exclusão lógica (D-18) — cancela as revisões pendentes dos assuntos dela (D-17).")
    public ResponseEntity<Void> arquivar(@PathVariable Long id) {
        disciplinaService.arquivar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reativar")
    @Operation(summary = "Reativa uma disciplina arquivada", description = "Simétrica de arquivar (D-49) — nunca reescreve a revisão cancelada, cria uma pendente nova no mesmo nível.")
    public ResponseEntity<Void> reativar(@PathVariable Long id) {
        disciplinaService.reativar(id);
        return ResponseEntity.noContent().build();
    }
}
