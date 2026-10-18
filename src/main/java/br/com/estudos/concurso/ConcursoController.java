package br.com.estudos.concurso;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/concursos")
@Tag(name = "Concursos", description = "Concurso-alvo e histórico de editais (ADR-042, docs/SPRINT-18-CONCURSOS.md)")
public class ConcursoController {

    private final ConcursoService concursoService;

    public ConcursoController(ConcursoService concursoService) {
        this.concursoService = concursoService;
    }

    @GetMapping
    @Operation(summary = "Histórico de concursos", description = "O ativo primeiro, depois do mais recente ao mais antigo, com total de assuntos e quantos em comum com o ativo.")
    public List<ConcursoResponse> historico() {
        return concursoService.historico().stream().map(concursoService::resposta).toList();
    }

    @GetMapping("/ativo")
    @Operation(summary = "Concurso-alvo", description = "Com os dias até a prova. 204 quando nenhum concurso é o alvo.")
    public ResponseEntity<ConcursoResponse> ativo() {
        return concursoService.historico().stream()
            .filter(c -> c.concurso().isAtivo())
            .findFirst()
            .map(c -> ResponseEntity.ok(concursoService.resposta(c)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{id}/planejamento")
    @Operation(summary = "Cobertura do concurso para o planejamento", description = "Nome, slug, data da prova e os assuntos do edital na ordem dele, com peso e chave externa. Só leitura; é a entrada da triagem no projeto de planejamento.")
    public PlanejamentoConcursoResponse paraPlanejamento(@PathVariable Long id) {
        return concursoService.paraPlanejamento(id);
    }

    @GetMapping("/{id}/material")
    @Operation(summary = "Material de cada assunto do concurso", description = "Na ordem do edital: situação (pronto, sem material, a preparar), segmentos, minutos, páginas e, nos sem material, o motivo da lacuna. Derivado; só leitura.")
    public List<MaterialAssuntoResponse> materialPorAssunto(@PathVariable Long id) {
        return concursoService.materialPorAssunto(id);
    }

    @PostMapping("/{id}/material/refazer")
    @Operation(summary = "Refazer o material do concurso (D-66)", description = "Arquiva segmentos e descarta lacunas de todo assunto da cobertura; o planejamento prepara de novo. Nada é apagado.")
    public ConcursoResponse refazerMaterial(@PathVariable Long id) {
        concursoService.refazerMaterial(id);
        return concursoService.resposta(id);
    }

    @PostMapping("/{id}/ativar")
    @Operation(summary = "Reativa um concurso do histórico", description = "Aplica de novo a cobertura do edital dele (D-60) e encerra o período do atual (D-59).")
    public ConcursoResponse ativar(@PathVariable Long id) {
        return concursoService.resposta(concursoService.reativar(id).getId());
    }
}
