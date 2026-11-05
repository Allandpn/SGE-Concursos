package br.com.estudos.qconcursos;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** O atalho para o QConcursos e a banca no catálogo (docs/SPRINT-36-QCONCURSOS.md §2; D-150 a D-152; ADR-058). */
@RestController
@Tag(name = "QConcursos", description = "Endereço filtrado montado pelo SGE; nada é lido do QConcursos")
public class QConcursosController {

    public record Endereco(String endereco) {}

    public record Vinculo(List<Long> noIds) {}

    private final QConcursosService qconcursos;
    private final VinculoQConcursosService vinculos;

    public QConcursosController(QConcursosService qconcursos, VinculoQConcursosService vinculos) {
        this.qconcursos = qconcursos;
        this.vinculos = vinculos;
    }

    @GetMapping("/api/assuntos/{id}/qconcursos-vinculo")
    @Operation(summary = "Os nós da árvore do QConcursos ligados ao assunto pela pessoa (D-173)", description = "naArvore=false: o nó saiu da árvore vigente.")
    public List<VinculoQConcursosService.NoLigado> vinculo(@PathVariable long id) {
        return vinculos.listar(id);
    }

    @PutMapping("/api/assuntos/{id}/qconcursos-vinculo")
    @Operation(summary = "Liga o assunto a 1 a 3 nós da árvore vigente, trocando o vínculo anterior (D-173)",
        description = "VINCULO_VAZIO, VINCULO_DEMAIS, NO_INEXISTENTE (422); sem árvore carregada, 404 ARVORE_NAO_CARREGADA.")
    public List<VinculoQConcursosService.NoLigado> ligar(@PathVariable long id, @RequestBody Vinculo corpo) {
        return vinculos.ligar(id, corpo.noIds());
    }

    @DeleteMapping("/api/assuntos/{id}/qconcursos-vinculo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Tira o vínculo; o atalho volta aos nós do edital (D-173)")
    public void tirar(@PathVariable long id) {
        vinculos.tirar(id);
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
