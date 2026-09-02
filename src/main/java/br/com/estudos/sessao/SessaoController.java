package br.com.estudos.sessao;

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

/** Endpoints de sessão (docs/SPRINT-3-SESSAO.md §4). Nenhuma exceção é tratada aqui — GlobalExceptionHandler cuida disso. */
@RestController
@RequestMapping("/api/sessoes")
@Tag(name = "Sessões", description = "Evento de estudo, questões, flashcards ou recuperação — nunca arquivada (docs/SPRINT-3-SESSAO.md §4)")
public class SessaoController {

    private final SessaoService sessaoService;

    public SessaoController(SessaoService sessaoService) {
        this.sessaoService = sessaoService;
    }

    @PostMapping
    @Operation(summary = "Registra uma sessão", description = "Reenviar a mesma tentativaId devolve o registro original em vez de duplicar (D-45).")
    public ResponseEntity<SessaoResponse> registrar(@RequestBody SessaoRequest request, UriComponentsBuilder uriBuilder) {
        var sessao = sessaoService.registrar(request);
        var uri = uriBuilder.path("/api/sessoes/{id}").buildAndExpand(sessao.getId()).toUri();
        return ResponseEntity.created(uri).body(SessaoMapper.toResponse(sessao));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma sessão pelo id")
    public SessaoResponse buscar(@PathVariable Long id) {
        return SessaoMapper.toResponse(sessaoService.buscar(id));
    }

    @GetMapping
    @Operation(summary = "Lista as sessões de um assunto")
    public List<SessaoResponse> listar(@RequestParam Long assuntoId) {
        return sessaoService.listar(assuntoId).stream().map(SessaoMapper::toResponse).toList();
    }
}
