package br.com.estudos.arvore;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.estudos.arvore.ArvoreResponses.Diferenca;
import br.com.estudos.arvore.ArvoreResponses.No;
import br.com.estudos.arvore.ArvoreResponses.Versao;

/** Busca e navegação na árvore do QConcursos para a tela de conferência (docs/SPRINT-25-CONFERENCIA-EDITAL.md §5). */
@RestController
@RequestMapping("/api/arvore")
@Tag(name = "Árvore do QConcursos", description = "Nós de assunto para ligar as partes do edital (01_DOMINIO §3.12, D-90)")
public class ArvoreController {

    private final ArvoreService service;

    public ArvoreController(ArvoreService service) {
        this.service = service;
    }

    @GetMapping("/busca")
    @Operation(summary = "Busca nós por texto", description = "Sem acento nem caixa; todas as palavras no nome ou no caminho. Na versão vigente, ou na pedida.")
    public List<No> buscar(@RequestParam("q") String consulta,
                           @RequestParam(value = "limite", defaultValue = "30") int limite,
                           @RequestParam(value = "versao", required = false) Long versao) {
        return service.buscar(consulta, limite, versao);
    }

    @GetMapping("/raizes")
    @Operation(summary = "As disciplinas da árvore", description = "O ponto de partida da navegação por ramos. O id da disciplina é negativo.")
    public List<No> raizes(@RequestParam(value = "versao", required = false) Long versao) {
        return service.raizes(versao);
    }

    @GetMapping("/nos/{id}")
    @Operation(summary = "Um nó, com o caminho completo e o número de filhos")
    public No no(@PathVariable long id, @RequestParam(value = "versao", required = false) Long versao) {
        return service.no(id, versao);
    }

    @GetMapping("/nos/{id}/filhos")
    @Operation(summary = "Os filhos diretos de um nó")
    public List<No> filhos(@PathVariable long id, @RequestParam(value = "versao", required = false) Long versao) {
        return service.filhos(id, versao);
    }

    @GetMapping("/versoes")
    @Operation(summary = "As versões carregadas", description = "Com a contagem do que mudou contra a vigente de quando foram carregadas.")
    public List<Versao> versoes() {
        return service.versoes();
    }

    @GetMapping("/versoes/{id}/diferenca")
    @Operation(summary = "O que mudou nesta versão", description = "Nós novos, removidos, renomeados e movidos contra a vigente de quando foi carregada.")
    public List<Diferenca> diferenca(@PathVariable long id) {
        return service.diferenca(id);
    }
}
