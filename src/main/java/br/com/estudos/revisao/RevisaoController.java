package br.com.estudos.revisao;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints de revisão (docs/SPRINT-4-ESCADA.md §4). Só leitura — Revisao nasce só como efeito de registrar sessão. */
@RestController
@RequestMapping("/api/revisoes")
@Tag(name = "Revisões", description = "Agendamento da escada — nasce só como efeito de sessão, nunca por POST direto (docs/SPRINT-4-ESCADA.md §4)")
public class RevisaoController {

    private final RevisaoService revisaoService;

    public RevisaoController(RevisaoService revisaoService) {
        this.revisaoService = revisaoService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma revisão pelo id")
    public RevisaoResponse buscar(@PathVariable Long id) {
        return RevisaoMapper.toResponse(revisaoService.buscar(id));
    }

    @GetMapping
    @Operation(summary = "Lista as revisões de um assunto")
    public List<RevisaoResponse> listar(@RequestParam Long assuntoId) {
        return revisaoService.listar(assuntoId).stream().map(RevisaoMapper::toResponse).toList();
    }
}
