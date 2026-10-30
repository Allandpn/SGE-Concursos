package br.com.estudos.foco;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.estudos.foco.FaseFoco.Especie;
import br.com.estudos.foco.FocoService.FocoDoSegmento;
import br.com.estudos.foco.FocoService.Livres;
import br.com.estudos.foco.FocoService.Proposta;
import br.com.estudos.foco.FocoService.Resumo;
import br.com.estudos.foco.FocoService.Situacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Pomodoro (docs/SPRINT-29-POMODORO.md §3). O relógio é do servidor; o cliente só desenha o ponteiro (ADR-050). */
@RestController
@RequestMapping("/api/foco")
@Tag(name = "Pomodoro", description = "Foco cronometrado que mede o tempo real de estudo (01_DOMINIO §3.15, D-98 a D-105)")
public class FocoController {

    public record Inicio(UUID tentativaId, Especie especie, Long assuntoId, Long segmentoId) {}

    public record Vinculo(Long assuntoId, Long segmentoId) {}

    private final FocoService foco;
    private final Clock clock;

    public FocoController(FocoService foco, Clock clock) {
        this.foco = foco;
        this.clock = clock;
    }

    @GetMapping("/atual")
    @Operation(summary = "A fase em curso", description = "Encerra antes a que venceu. 204 se não há fase em curso.")
    public ResponseEntity<Situacao> atual() {
        return foco.atual().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/proposta")
    @Operation(summary = "A próxima fase, proposta", description = "Só deriva; nenhuma fase começa sozinha (D-102). 204 se não há o que propor.")
    public ResponseEntity<Proposta> proposta() {
        return foco.proposta().map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/iniciar")
    @Operation(summary = "Inicia uma fase", description = "409 se já há uma em curso (D-98). Repetir a tentativaId devolve a mesma fase.")
    public FaseFoco iniciar(@RequestBody Inicio corpo) {
        return foco.iniciar(corpo.tentativaId(), corpo.especie(), corpo.assuntoId(), corpo.segmentoId());
    }

    @PostMapping("/atual/pausar")
    public Situacao pausar() {
        return foco.pausar();
    }

    @PostMapping("/atual/retomar")
    public Situacao retomar() {
        return foco.retomar();
    }

    @PostMapping("/atual/encerrar")
    public FaseFoco encerrar() {
        return foco.encerrar();
    }

    @PutMapping("/{id}/assunto")
    @Operation(summary = "Aponta um foco para um assunto (e segmento)", description = "Vale para foco em curso ou encerrado ainda sem sessão.")
    public FaseFoco apontar(@PathVariable Long id, @RequestBody Vinculo corpo) {
        return foco.apontar(id, corpo.assuntoId(), corpo.segmentoId());
    }

    @GetMapping("/livres")
    @Operation(summary = "Focos do assunto que nenhuma sessão usou", description = "A soma pré-preenche o tempo do registro (D-100).")
    public Livres livres(@RequestParam Long assuntoId) {
        return foco.livres(assuntoId);
    }

    @GetMapping("/resumo")
    @Operation(summary = "Foco por dia e focos completos × encerrados antes (D-105)")
    public Resumo resumo(@RequestParam(required = false) LocalDate de, @RequestParam(required = false) LocalDate ate) {
        var fim = ate != null ? ate : LocalDate.now(clock);
        return foco.resumo(de != null ? de : fim.minusDays(6), fim);
    }

    @GetMapping("/assuntos/{id}/segmentos")
    @Operation(summary = "Estimado × real por segmento (D-105)")
    public List<FocoDoSegmento> segmentos(@PathVariable Long id) {
        return foco.porSegmento(id);
    }
}
