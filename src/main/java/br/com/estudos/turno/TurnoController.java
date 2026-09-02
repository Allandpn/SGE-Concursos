package br.com.estudos.turno;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoint do plano de turno (docs/SPRINT-6-TURNO.md §3). Só leitura — nada aqui grava. */
@RestController
@RequestMapping("/api/turno")
@Tag(name = "Turno", description = "Plano de hoje: fila de recuperação e bloco de conteúdo novo — derivado, efêmero (docs/SPRINT-6-TURNO.md §3)")
public class TurnoController {

    private final TurnoService turnoService;

    public TurnoController(TurnoService turnoService) {
        this.turnoService = turnoService;
    }

    @GetMapping("/plano")
    @Operation(summary = "Monta o plano do turno de hoje", description = "Fila de recuperação (respeitando o teto diário) mais um bloco de conteúdo novo, se houver vaga na frente.")
    public TurnoPlanoResponse plano() {
        return turnoService.plano();
    }
}
