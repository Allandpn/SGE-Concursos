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

/** Endpoints de disciplina (docs/SPRINT-2-CADASTRO.md §4). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/disciplinas")
public class DisciplinaController {

    private final DisciplinaService disciplinaService;

    public DisciplinaController(DisciplinaService disciplinaService) {
        this.disciplinaService = disciplinaService;
    }

    @GetMapping
    public List<DisciplinaResponse> listar() {
        return disciplinaService.listar().stream().map(DisciplinaMapper::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<DisciplinaResponse> criar(@RequestBody DisciplinaRequest request, UriComponentsBuilder uriBuilder) {
        var disciplina = disciplinaService.criar(request);
        var uri = uriBuilder.path("/api/disciplinas/{id}").buildAndExpand(disciplina.getId()).toUri();
        return ResponseEntity.created(uri).body(DisciplinaMapper.toResponse(disciplina));
    }

    @PatchMapping("/{id}")
    public DisciplinaResponse atualizar(@PathVariable Long id, @RequestBody DisciplinaRequest request) {
        return DisciplinaMapper.toResponse(disciplinaService.atualizar(id, request));
    }

    @PostMapping("/{id}/arquivar")
    public ResponseEntity<Void> arquivar(@PathVariable Long id) {
        disciplinaService.arquivar(id);
        return ResponseEntity.noContent().build();
    }
}
