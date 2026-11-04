package br.com.estudos.qconcursos;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** O atalho para o QConcursos e a banca no catálogo (docs/SPRINT-36-QCONCURSOS.md §2; D-150 a D-152; ADR-058). */
@RestController
@Tag(name = "QConcursos", description = "Endereço filtrado montado pelo SGE; nada é lido do QConcursos")
public class QConcursosController {

    public record Endereco(String endereco) {}

    private final QConcursosService qconcursos;

    public QConcursosController(QConcursosService qconcursos) {
        this.qconcursos = qconcursos;
    }

    @GetMapping("/api/qconcursos/atalhos")
    @Operation(summary = "O atalho de cada assunto pedido: endereço, quantas sugerir, filtro e avisos (D-150 a D-152)")
    public List<QConcursosService.Atalho> atalhos(@RequestParam(required = false) List<Long> assuntoIds) {
        return qconcursos.atalhos(assuntoIds);
    }

    @GetMapping("/api/concursos/{id}/banca-qconcursos")
    @Operation(summary = "A banca do concurso e o número dela no catálogo do QConcursos (D-151)")
    public QConcursosService.BancaDoConcurso banca(@PathVariable long id) {
        return qconcursos.bancaDoConcurso(id);
    }

    @PutMapping("/api/concursos/{id}/banca-qconcursos")
    @Operation(summary = "Aprende o número da banca por um endereço do QConcursos filtrado por ela (D-151)")
    public QConcursosService.BancaDoConcurso aprender(@PathVariable long id, @RequestBody Endereco corpo) {
        return qconcursos.aprenderBanca(id, corpo.endereco());
    }
}
