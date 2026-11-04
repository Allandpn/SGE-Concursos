package br.com.estudos.ensaio;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.sessao.SessaoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Os próximos 7 dias e o ensaio (docs/SPRINT-38-PROXIMOS-DIAS-E-ENSAIO.md §2; D-157, D-159; ADR-059). */
@RestController
@Tag(name = "Próximos dias e ensaio", description = "Visão derivada da escada e o registro ensaiado, sempre desfeito")
public class EnsaioController {

    private final ProximosDiasService proximosDias;
    private final EnsaioService ensaio;

    public EnsaioController(ProximosDiasService proximosDias, EnsaioService ensaio) {
        this.proximosDias = proximosDias;
        this.ensaio = ensaio;
    }

    @GetMapping("/api/turno/proximos-dias")
    @Operation(summary = "As revisões previstas de amanhã a +7, contra o teto, com a reta final e a estimativa de assuntos novos (D-157)")
    public ProximosDiasService.ProximosDias proximos() {
        return proximosDias.proximos();
    }

    @PostMapping("/api/sessoes/ensaio")
    @Operation(summary = "O que aconteceria com esta sessão: o registro real, desfeito; nada é gravado (D-159, ADR-059)")
    public EnsaioService.Ensaio ensaiar(@RequestBody SessaoRequest corpo) {
        return ensaio.ensaiar(corpo);
    }
}
