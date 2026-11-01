package br.com.estudos.diagnostico;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** O diagnóstico do edital (docs/SPRINT-20-DIAGNOSTICO-EDITAL.md §2; D-137, D-138, D-139). */
@RestController
@Tag(name = "Diagnóstico do edital", description = "Cobertura por fase, valor em pontos e o que cabe até a data; só leitura")
public class DiagnosticoController {

    private final DiagnosticoService diagnostico;

    public DiagnosticoController(DiagnosticoService diagnostico) {
        this.diagnostico = diagnostico;
    }

    @GetMapping("/api/concursos/{id}/diagnostico")
    @Operation(summary = "O diagnóstico de um concurso, alvo ou não (D-137 a D-139)",
        description = "data (AAAA-MM-DD, futura) e ritmo (assuntos novos por semana) são simulação e nunca são gravados.")
    public DiagnosticoService.DiagnosticoResponse diagnostico(@PathVariable long id,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
        @RequestParam(required = false) BigDecimal ritmo) {
        return diagnostico.diagnostico(id, data, ritmo);
    }
}
