package br.com.estudos.metrica;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints de métrica (docs/SPRINT-7-METRICAS.md §5). Só leitura. */
@RestController
@RequestMapping("/api/metricas")
@Tag(name = "Métricas", description = "M-1 a M-4, calculadas por consulta, nunca persistidas (ADR-033)")
public class MetricaController {

    private final MetricaService metricaService;

    public MetricaController(MetricaService metricaService) {
        this.metricaService = metricaService;
    }

    @GetMapping("/m1")
    @Operation(summary = "M-1: aderência ao objetivo", description = "Percentual de acerto por disciplina e formato, mais a linha GLOBAL.")
    public M1Response m1(@RequestParam JanelaMetrica janela) {
        return metricaService.m1(janela);
    }

    @GetMapping("/m2")
    @Operation(summary = "M-2: retenção por tipo de sessão", description = "Primeira exposição vs. média das seguintes, por tipo de sessão de um assunto.")
    public M2Response m2(@RequestParam Long assuntoId) {
        return metricaService.m2(assuntoId);
    }

    @GetMapping("/m3")
    @Operation(summary = "M-3: calibração da autoavaliação", description = "Variante objetiva (QUESTOES/FLASHCARDS) e subjetiva (RECUPERACAO), nunca somadas.")
    public M3Response m3() {
        return metricaService.m3();
    }

    @GetMapping("/m4")
    @Operation(summary = "M-4: aderência ao prazo", description = "Percentual de revisões cumpridas dentro de três dias do previsto.")
    public M4Response m4() {
        return metricaService.m4();
    }
}
