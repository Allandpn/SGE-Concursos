package br.com.estudos.metrica;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de métrica (docs/SPRINT-7-METRICAS.md §5). Só leitura. */
@RestController
@RequestMapping("/api/metricas")
public class MetricaController {

    private final MetricaService metricaService;

    public MetricaController(MetricaService metricaService) {
        this.metricaService = metricaService;
    }

    @GetMapping("/m1")
    public M1Response m1(@RequestParam JanelaMetrica janela) {
        return metricaService.m1(janela);
    }

    @GetMapping("/m2")
    public M2Response m2(@RequestParam Long assuntoId) {
        return metricaService.m2(assuntoId);
    }

    @GetMapping("/m3")
    public M3Response m3() {
        return metricaService.m3();
    }

    @GetMapping("/m4")
    public M4Response m4() {
        return metricaService.m4();
    }
}
