package br.com.estudos.reinicio;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Reiniciar o progresso (04_FRONTEND §8E; D-163 a D-165). Sem concursoId: tudo. */
@RestController
@RequestMapping("/api/reinicios")
@Tag(name = "Reinícios", description = "Reiniciar o progresso de um concurso ou de tudo, e desfazer (01_DOMINIO §3.27)")
public class ReinicioController {

    public record Pedido(Long concursoId, String confirmacao) {}

    private final ReinicioService service;

    public ReinicioController(ReinicioService service) {
        this.service = service;
    }

    @GetMapping("/previa")
    @Operation(summary = "O que vai zerar: sessões, assuntos e, num concurso, os divididos com outros (D-165)")
    public ReinicioService.Previa previa(@RequestParam(required = false) Long concursoId) {
        return service.previa(concursoId);
    }

    @PostMapping
    @Operation(summary = "Reinicia (D-163); a confirmação é a palavra REINICIAR (D-165)")
    public ReinicioService.Feito reiniciar(@RequestBody Pedido pedido) {
        return service.reiniciar(pedido.concursoId(), pedido.confirmacao());
    }

    @GetMapping
    @Operation(summary = "Os reinícios, do mais novo ao mais antigo")
    public List<ReinicioService.Linha> listar() {
        return service.listar();
    }

    @PostMapping("/{id}/desfazer")
    @Operation(summary = "Desfaz o reinício: as sessões dele que ainda estiverem tiradas voltam (D-164)")
    public ReinicioService.Feito desfazer(@PathVariable long id) {
        return service.desfazer(id);
    }
}
