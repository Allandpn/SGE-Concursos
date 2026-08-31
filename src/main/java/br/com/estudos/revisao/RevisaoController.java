package br.com.estudos.revisao;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de revisão (docs/SPRINT-4-ESCADA.md §4). Só leitura — Revisao nasce só como efeito de registrar sessão. */
@RestController
@RequestMapping("/api/revisoes")
public class RevisaoController {

    private final RevisaoService revisaoService;

    public RevisaoController(RevisaoService revisaoService) {
        this.revisaoService = revisaoService;
    }

    @GetMapping("/{id}")
    public RevisaoResponse buscar(@PathVariable Long id) {
        return RevisaoMapper.toResponse(revisaoService.buscar(id));
    }

    @GetMapping
    public List<RevisaoResponse> listar(@RequestParam Long assuntoId) {
        return revisaoService.listar(assuntoId).stream().map(RevisaoMapper::toResponse).toList();
    }
}
