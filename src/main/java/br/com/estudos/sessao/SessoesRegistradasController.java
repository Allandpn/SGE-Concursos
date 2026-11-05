package br.com.estudos.sessao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Sessões registradas (04_FRONTEND §8D; D-160 a D-162): listar por dia, tirar da escada e pôr de volta. */
@RestController
@RequestMapping("/api/sessoes/registradas")
@Tag(name = "Sessões registradas", description = "Tirar sessões da escada e pôr de volta (01_DOMINIO §3.26)")
public class SessoesRegistradasController {

    public record Ids(List<Long> ids) {}

    private final SessoesRegistradasService service;

    public SessoesRegistradasController(SessoesRegistradasService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "As sessões de 30 dias até a data (hoje, sem ela), ativas e tiradas, da mais nova para a mais antiga")
    public SessoesRegistradasService.Pagina listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.listar(ate);
    }

    @PostMapping("/tirar")
    @Operation(summary = "Tira as sessões da escada (D-160) e refaz a escada dos assuntos (D-161)")
    public SessoesRegistradasService.Feito tirar(@RequestBody Ids corpo) {
        return service.tirar(corpo.ids());
    }

    @PostMapping("/por-de-volta")
    @Operation(summary = "Põe as sessões de volta (D-162) e refaz a escada dos assuntos (D-161)")
    public SessoesRegistradasService.Feito porDeVolta(@RequestBody Ids corpo) {
        return service.porDeVolta(corpo.ids());
    }
}
