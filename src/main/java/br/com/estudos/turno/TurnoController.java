package br.com.estudos.turno;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint do plano de turno (docs/SPRINT-6-TURNO.md §3). Só leitura — nada aqui grava. */
@RestController
@RequestMapping("/api/turno")
public class TurnoController {

    private final TurnoService turnoService;

    public TurnoController(TurnoService turnoService) {
        this.turnoService = turnoService;
    }

    @GetMapping("/plano")
    public TurnoPlanoResponse plano() {
        return turnoService.plano();
    }
}
