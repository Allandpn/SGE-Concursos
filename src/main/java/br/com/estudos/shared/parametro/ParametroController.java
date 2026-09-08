package br.com.estudos.shared.parametro;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints de parâmetro (docs/SPRINT-15-AJUSTES.md §2). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/parametros")
@Tag(name = "Parâmetros", description = "Configuração, não domínio (01_DOMINIO §3) — lista fechada de 18 chaves, sem POST/DELETE")
public class ParametroController {

    private final ParametroService parametroService;

    public ParametroController(ParametroService parametroService) {
        this.parametroService = parametroService;
    }

    @GetMapping
    @Operation(summary = "Lista os parâmetros de configuração")
    public List<ParametroResponse> listar() {
        return parametroService.listar().stream().map(ParametroMapper::toResponse).toList();
    }

    @PatchMapping("/{chave}")
    @Operation(summary = "Atualiza o valor de um parâmetro", description = "Validação única: número positivo, sem faixa por chave.")
    public ParametroResponse atualizar(@PathVariable String chave, @RequestBody AtualizarParametroRequest request) {
        return ParametroMapper.toResponse(parametroService.atualizar(chave, request));
    }
}
