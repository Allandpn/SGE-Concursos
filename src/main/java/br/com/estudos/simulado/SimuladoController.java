package br.com.estudos.simulado;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Endpoints de simulado (docs/SPRINT-8-SIMULADO.md §5). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/simulados")
public class SimuladoController {

    private final SimuladoService simuladoService;

    public SimuladoController(SimuladoService simuladoService) {
        this.simuladoService = simuladoService;
    }

    @PostMapping
    public ResponseEntity<SimuladoResponse> registrar(@RequestBody SimuladoRequest request, UriComponentsBuilder uriBuilder) {
        var registrado = simuladoService.registrar(request);
        var uri = uriBuilder.path("/api/simulados/{id}").buildAndExpand(registrado.simulado().getId()).toUri();
        return ResponseEntity.created(uri).body(SimuladoMapper.toResponse(registrado.simulado(), registrado.resultados()));
    }

    @GetMapping
    public List<SimuladoResponse> listar() {
        return simuladoService.listar().stream()
            .map(simulado -> SimuladoMapper.toResponse(simulado, simuladoService.listarResultados(simulado.getId())))
            .toList();
    }
}
