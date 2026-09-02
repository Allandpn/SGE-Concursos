package br.com.estudos.simulado;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints de simulado (docs/SPRINT-8-SIMULADO.md §5). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/simulados")
@Tag(name = "Simulados", description = "Resultado por disciplina de um simulado completo, tudo-ou-nada (docs/SPRINT-8-SIMULADO.md §5)")
public class SimuladoController {

    private final SimuladoService simuladoService;

    public SimuladoController(SimuladoService simuladoService) {
        this.simuladoService = simuladoService;
    }

    @PostMapping
    @Operation(summary = "Registra um simulado", description = "Grava todos os resultados por disciplina ou nenhum (regra 1: tudo ou nada).")
    public ResponseEntity<SimuladoResponse> registrar(@RequestBody SimuladoRequest request, UriComponentsBuilder uriBuilder) {
        var registrado = simuladoService.registrar(request);
        var uri = uriBuilder.path("/api/simulados/{id}").buildAndExpand(registrado.simulado().getId()).toUri();
        return ResponseEntity.created(uri).body(SimuladoMapper.toResponse(registrado.simulado(), registrado.resultados()));
    }

    @GetMapping
    @Operation(summary = "Lista os simulados com seus resultados")
    public List<SimuladoResponse> listar() {
        return simuladoService.listarComResultados().stream()
            .map(registrado -> SimuladoMapper.toResponse(registrado.simulado(), registrado.resultados()))
            .toList();
    }
}
