package br.com.estudos.erro;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Endpoints de erro (docs/SPRINT-7-METRICAS.md §5). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/erros")
public class ErroController {

    private final ErroService erroService;

    public ErroController(ErroService erroService) {
        this.erroService = erroService;
    }

    @PostMapping
    public ResponseEntity<ErroResponse> registrar(@RequestBody ErroRequest request, UriComponentsBuilder uriBuilder) {
        var erro = erroService.registrar(request);
        var uri = uriBuilder.path("/api/erros/{id}").buildAndExpand(erro.getId()).toUri();
        return ResponseEntity.created(uri).body(ErroMapper.toResponse(erro));
    }

    @GetMapping
    public List<ErroResponse> listar(@RequestParam Long assuntoId) {
        return erroService.listarPorAssunto(assuntoId).stream().map(ErroMapper::toResponse).toList();
    }
}
