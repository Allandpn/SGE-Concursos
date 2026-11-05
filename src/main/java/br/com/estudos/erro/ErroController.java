package br.com.estudos.erro;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints de erro (docs/SPRINT-7-METRICAS.md §5). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/erros")
@Tag(name = "Erros", description = "Banco de erros cometidos — causa e confiança apontadas pelo usuário (docs/SPRINT-7-METRICAS.md §5)")
public class ErroController {

    private final ErroService erroService;
    private final ErrosGeraisService gerais;

    public record Resolvido(boolean resolvido) {}

    public ErroController(ErroService erroService, ErrosGeraisService gerais) {
        this.erroService = erroService;
        this.gerais = gerais;
    }

    @PostMapping
    @Operation(summary = "Registra um erro")
    public ResponseEntity<ErroResponse> registrar(@RequestBody ErroRequest request, UriComponentsBuilder uriBuilder) {
        var erro = erroService.registrar(request);
        var uri = uriBuilder.path("/api/erros/{id}").buildAndExpand(erro.getId()).toUri();
        return ResponseEntity.created(uri).body(ErroMapper.toResponse(erro));
    }

    @GetMapping
    @Operation(summary = "Lista os erros de um assunto")
    public List<ErroResponse> listar(@RequestParam Long assuntoId) {
        return erroService.listarPorAssunto(assuntoId).stream().map(ErroMapper::toResponse).toList();
    }

    @GetMapping("/todos")
    @Operation(summary = "A lista geral, do mais novo ao mais antigo (Sprint 40): filtros por disciplina, causa e só não resolvidos")
    public List<ErrosGeraisService.ErroGeral> todos(@RequestParam(required = false) Long disciplinaId, @RequestParam(required = false) String causa,
            @RequestParam(defaultValue = "false") boolean naoResolvidos) {
        return gerais.listar(disciplinaId, causa, naoResolvidos);
    }

    @PostMapping("/{id}/resolvido")
    @Operation(summary = "Marca ou desmarca o erro como resolvido")
    public ResponseEntity<Void> resolver(@PathVariable long id, @RequestBody Resolvido corpo) {
        gerais.resolver(id, corpo.resolvido());
        return ResponseEntity.noContent().build();
    }
}
