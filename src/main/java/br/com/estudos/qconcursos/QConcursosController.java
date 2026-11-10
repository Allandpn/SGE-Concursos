package br.com.estudos.qconcursos;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final FiltroQConcursosService filtros;

    public QConcursosController(QConcursosService qconcursos, VinculoQConcursosService vinculos, FiltroQConcursosService filtros) {
        this.qconcursos = qconcursos;
        this.vinculos = vinculos;
        this.filtros = filtros;
    }

    @GetMapping("/api/qconcursos/filtro")
    @Operation(summary = "O filtro padrão do atalho do QConcursos (D-175)")
    public FiltroQConcursosService.Filtro filtroPadrao() {
        return filtros.padrao();
    }

    @PutMapping("/api/qconcursos/filtro")
    @Operation(summary = "Troca o filtro padrão inteiro (D-175, D-176)", description = "FILTRO_INCOMPLETO, FILTRO_INVALIDO, NOME_FALTANDO (422).")
    public FiltroQConcursosService.Filtro salvarFiltroPadrao(@RequestBody FiltroQConcursosService.Filtro corpo) {
        return filtros.salvarPadrao(corpo);
    }

    @PostMapping("/api/qconcursos/filtro/endereco")
    @Operation(summary = "Lê cargo, áreas e instituição de um endereço do QConcursos, com o nome já guardado (D-176)",
        description = "Nada é gravado. ENDERECO_SEM_FILTRO (422).")
    public List<LinkQConcursos.Aprendido> lerEndereco(@RequestBody Endereco corpo) {
        return filtros.lerEndereco(corpo.endereco());
    }

    @GetMapping("/api/concursos/{id}/qconcursos-filtro")
    @Operation(summary = "A exceção do concurso (nula se não há) e o filtro que vale para ele (D-175)")
    public FiltroQConcursosService.DoConcurso filtroDoConcurso(@PathVariable long id) {
        return filtros.doConcurso(id);
    }

    @PutMapping("/api/concursos/{id}/qconcursos-filtro")
    @Operation(summary = "Grava a exceção do concurso: só o que difere; nulo segue o padrão (D-175)")
    public FiltroQConcursosService.DoConcurso salvarFiltroDoConcurso(@PathVariable long id, @RequestBody FiltroQConcursosService.Filtro corpo) {
        return filtros.salvarExcecao(id, corpo);
    }

    @DeleteMapping("/api/concursos/{id}/qconcursos-filtro")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Volta o concurso ao filtro padrão (D-175)")
    public void voltarAoPadrao(@PathVariable long id) {
        filtros.voltarAoPadrao(id);
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
