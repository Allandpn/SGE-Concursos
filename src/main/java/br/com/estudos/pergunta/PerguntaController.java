package br.com.estudos.pergunta;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Perguntas de recuperação (docs/SPRINT-32-PERGUNTAS.md §3; D-124 a D-127, ADR-053). */
@RestController
@Tag(name = "Perguntas", description = "Perguntas de recuperação preparadas pela IA e o gabarito (D-122 a D-128)")
public class PerguntaController {

    private final PerguntaService perguntas;

    public PerguntaController(PerguntaService perguntas) {
        this.perguntas = perguntas;
    }

    @GetMapping("/api/assuntos/{id}/perguntas")
    @Operation(summary = "3 a 5 perguntas para a etapa 1 da recuperação, sem gabarito (D-124, D-125)", description = "Vazio: recuperação livre.")
    public List<PerguntaService.Pergunta> selecionar(@PathVariable long id) {
        return perguntas.selecionar(id);
    }

    @GetMapping("/api/perguntas/gabarito")
    @Operation(summary = "O gabarito em pontos, só para a etapa 2 (D-125)")
    public List<PerguntaService.Gabarito> gabarito(@RequestParam List<Long> ids) {
        return perguntas.gabarito(ids);
    }

    @PostMapping("/api/perguntas/{id}/ruim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Pergunta ruim: sai do banco (D-127). Nada se apaga.")
    public void ruim(@PathVariable long id) {
        perguntas.marcarRuim(id, true);
    }

    @DeleteMapping("/api/perguntas/{id}/ruim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desfaz \"pergunta ruim\" (na mesma tela)")
    public void desfazerRuim(@PathVariable long id) {
        perguntas.marcarRuim(id, false);
    }

    @GetMapping("/api/assuntos/{id}/perguntas/estado")
    @Operation(summary = "O estado das perguntas de cada bloco do assunto: NAO_LIDO, PENDENTE, PRONTA, SEM_TEXTO ou FALHOU, e quantas")
    public List<PerguntaService.EstadoDoBloco> estado(@PathVariable long id) {
        return perguntas.estado(id);
    }
}
