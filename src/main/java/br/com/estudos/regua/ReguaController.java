package br.com.estudos.regua;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** A régua da prova e a nota de corte (docs/SPRINT-23-REGISTRO-E-PROVA-REAL.md §2; D-133, D-135, D-136; ADR-055). */
@RestController
@Tag(name = "Régua da prova", description = "Pontos por disciplina, errada anula certa e nota de corte; a nota em pontos é derivada (D-135, D-136)")
public class ReguaController {

    private final ReguaService regua;

    public ReguaController(ReguaService regua) {
        this.regua = regua;
    }

    @GetMapping("/api/concursos/{id}/regua")
    @Operation(summary = "A régua da prova do concurso (pontos por disciplina, errada anula certa, nota de corte)")
    public ReguaService.ReguaDoConcurso regua(@PathVariable long id) {
        return regua.regua(id);
    }

    @PutMapping("/api/concursos/{id}/regua")
    @Operation(summary = "Salva a régua (D-135); a nota em pontos de todo o histórico passa a usar a nova")
    public ReguaService.ReguaDoConcurso salvar(@PathVariable long id, @RequestBody ReguaService.ReguaDoConcurso corpo) {
        return regua.salvar(id, corpo);
    }

    @GetMapping("/api/provas/{id}/pontos")
    @Operation(summary = "A nota em pontos da prova resolvida (a primeira tentativa entregue), com a nota de corte (D-136)", description = "null sem tentativa entregue.")
    public Regua.Nota pontosDaProva(@PathVariable long id) {
        return regua.pontosDaProva(id).orElse(null);
    }

    @GetMapping("/api/simulados/pontos")
    @Operation(summary = "A nota em pontos de cada simulado, pela régua do concurso-alvo (D-136)")
    public List<ReguaService.PontosDoSimulado> pontosDosSimulados() {
        return regua.pontosDosSimulados();
    }

    @GetMapping("/api/assuntos/{id}/lote-sugerido")
    @Operation(summary = "O formato para o lote rápido de \"Fiz questões\" (D-133)")
    public Map<String, String> loteSugerido(@PathVariable long id) {
        return Map.of("formato", regua.formatoSugerido(id));
    }
}
