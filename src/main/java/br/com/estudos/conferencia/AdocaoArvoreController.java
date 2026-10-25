package br.com.estudos.conferencia;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.estudos.conferencia.AdocaoArvoreService.Impacto;

/** Atualizar a árvore do QConcursos por gesto da pessoa (docs/SPRINT-25-CONFERENCIA-EDITAL.md §8, D-90). */
@RestController
@RequestMapping("/api/arvore/versoes/{id}")
@Tag(name = "Árvore do QConcursos", description = "Nós de assunto para ligar as partes do edital (01_DOMINIO §3.12, D-90)")
public class AdocaoArvoreController {

    private final AdocaoArvoreService service;

    public AdocaoArvoreController(AdocaoArvoreService service) {
        this.service = service;
    }

    @GetMapping("/impacto")
    @Operation(summary = "O que mudaria se esta versão fosse adotada", description = "Partes cujo nó saiu ou mudou de pai (voltam à fila), nós renomeados (seguem) e decisões afetadas.")
    public Impacto impacto(@PathVariable long id) {
        return service.impacto(id);
    }

    @PostMapping("/adotar")
    @Operation(summary = "Adota a versão", description = "Sem confirmar=true só mostra o impacto. Com ele, a versão passa a vigente; a parte cujo nó saiu ou mudou de pai volta à fila para reconfirmar.")
    public Impacto adotar(@PathVariable long id, @RequestParam(value = "confirmar", defaultValue = "false") boolean confirmar) {
        return service.adotar(id, confirmar);
    }
}
