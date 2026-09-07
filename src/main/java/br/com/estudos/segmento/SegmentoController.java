package br.com.estudos.segmento;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Leitura de segmentos de um assunto (docs/SPRINT-10-SEGMENTO.md §5). */
@RestController
@RequestMapping("/api/assuntos/{assuntoId}/segmentos")
@Tag(name = "Segmentos", description = "Pedaços de material de leitura de um assunto, nascidos só por importação (01_DOMINIO §3.7)")
public class SegmentoController {

    private final SegmentoService segmentoService;

    public SegmentoController(SegmentoService segmentoService) {
        this.segmentoService = segmentoService;
    }

    @GetMapping
    @Operation(summary = "Lista os segmentos de um assunto, na ordem de leitura")
    public List<SegmentoResponse> listar(@PathVariable Long assuntoId) {
        return segmentoService.listarPorAssunto(assuntoId).stream().map(SegmentoMapper::toResponse).toList();
    }
}
