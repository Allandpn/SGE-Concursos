package br.com.estudos.arvore;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.databind.JsonNode;

import br.com.estudos.arvore.ArvoreResponses.Versao;

/**
 * A árvore chega do planejamento (docs/SPRINT-25-CONFERENCIA-EDITAL.md §4). O token é checado antes, por
 * TokenInternoFilter. Carregar não troca a versão vigente: isso é gesto da pessoa (D-90).
 */
@RestController
@RequestMapping("/internal/arvores")
@Tag(name = "Interno · árvore", description = "Só para o planejamento no mesmo servidor; exige X-Internal-Token (ADR-044)")
public class ArvoreInternoController {

    private final ArvoreService service;

    public ArvoreInternoController(ArvoreService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Carrega uma versão da árvore", description = "O formato de taxonomia/qconcursos-AAAA-MM-DD.json. Idempotente pelo rótulo (fonte-data). A primeira vira vigente sozinha; as outras esperam a pessoa adotar.")
    public Versao carregar(@RequestBody JsonNode corpo) {
        return service.carregar(corpo);
    }
}
