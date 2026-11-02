package br.com.estudos.pesobanca;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** O peso pela banca (docs/SPRINT-22-PESO-PELA-BANCA.md §2; D-145, D-146, D-147; ADR-057). */
@RestController
@Tag(name = "Peso pela banca", description = "Incidência medida nas provas hospedadas; a proposta é derivada e só entra por aceite")
public class PesoBancaController {

    public record Aceite(List<Long> assuntoIds) {}

    private final PesoBancaService pesoBanca;

    public PesoBancaController(PesoBancaService pesoBanca) {
        this.pesoBanca = pesoBanca;
    }

    @GetMapping("/api/concursos/{id}/peso-banca")
    @Operation(summary = "A incidência da banca, as propostas por disciplina e os ajustes vigentes (D-145, D-146)")
    public PesoBancaService.Situacao situacao(@PathVariable long id) {
        return pesoBanca.situacao(id);
    }

    @PostMapping("/api/concursos/{id}/peso-banca/aceitar")
    @Operation(summary = "Aceita as propostas destes assuntos (D-147); só o que é proposta agora entra")
    public PesoBancaService.Situacao aceitar(@PathVariable long id, @RequestBody Aceite corpo) {
        return pesoBanca.aceitar(id, corpo.assuntoIds());
    }

    @PostMapping("/api/concursos/{id}/peso-banca/{assuntoId}/desfazer")
    @Operation(summary = "Desfaz o ajuste: o assunto volta ao peso do edital (D-147)")
    public PesoBancaService.Situacao desfazer(@PathVariable long id, @PathVariable long assuntoId) {
        return pesoBanca.desfazer(id, assuntoId);
    }
}
