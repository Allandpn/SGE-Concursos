package br.com.estudos.edital;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ia")
@Tag(name = "Ajustes de IA", description = "Chave e modelo do OpenRouter, cadastrados na tela; a chave nunca volta (ADR-044)")
public class AjusteIaController {

    private final AjusteIaService service;

    public AjusteIaController(AjusteIaService service) {
        this.service = service;
    }

    @GetMapping("/configuracao")
    @Operation(summary = "Configuração de IA em uso", description = "Só os quatro últimos caracteres da chave.")
    public AjusteIaResponse obter() {
        return service.obter();
    }

    @PutMapping("/configuracao")
    @Operation(summary = "Cadastra chave e/ou modelo", description = "Campo ausente ou em branco mantém o atual.")
    public AjusteIaResponse atualizar(@RequestBody AjusteIaRequest pedido) {
        return service.atualizar(pedido);
    }

    @GetMapping("/modelos")
    @Operation(summary = "Modelos do OpenRouter", description = "Lista pública, com preço por milhão de tokens. Sem cache (ADR-029).")
    public List<ModeloIa> modelos() {
        return service.modelos();
    }
}
