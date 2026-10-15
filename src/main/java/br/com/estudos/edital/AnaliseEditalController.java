package br.com.estudos.edital;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.estudos.concurso.ConcursoResponse;
import br.com.estudos.concurso.ConcursoService;

@RestController
@RequestMapping("/api/editais")
@Tag(name = "Edital novo", description = "Análise de edital por IA via OpenRouter — a IA extrai, o sistema decide, nada é gravado sem confirmar (ADR-043)")
public class AnaliseEditalController {

    private final AnaliseEditalService service;
    private final ConcursoService concursoService;

    public AnaliseEditalController(AnaliseEditalService service, ConcursoService concursoService) {
        this.service = service;
        this.concursoService = concursoService;
    }

    @GetMapping("/ia")
    @Operation(summary = "A análise por IA está disponível?", description = "Sem chave do OpenRouter, disponivel=false com o motivo.")
    public IaStatusResponse statusIa() {
        return service.statusIa();
    }

    @PostMapping(value = "/analises", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia o PDF do edital", description = "Extrai o texto e começa a etapa 1 (concurso e cargos) em segundo plano. 202.")
    public ResponseEntity<AnaliseEditalResponse> iniciar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.resposta(service.iniciar(arquivo)));
    }

    @GetMapping("/analises")
    @Operation(summary = "Análises recentes", description = "As 10 mais recentes, para retomar uma em andamento.")
    public List<AnaliseEditalResponse> recentes() {
        return service.recentes().stream().map(service::resposta).toList();
    }

    @GetMapping("/analises/{id}")
    @Operation(summary = "Estado de uma análise", description = "Com status PRONTA, traz a proposta e o resumo.")
    public AnaliseEditalResponse buscar(@PathVariable Long id) {
        return service.resposta(service.buscar(id));
    }

    @PostMapping("/analises/{id}/cargo")
    @Operation(summary = "Escolhe o cargo", description = "Só em ESCOLHER_CARGO. Começa a etapa 2 (disciplinas e assuntos). 202.")
    public ResponseEntity<AnaliseEditalResponse> escolherCargo(@PathVariable Long id, @RequestBody EscolhaCargoRequest corpo) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.resposta(service.escolherCargo(id, corpo.cargo())));
    }

    @PostMapping("/analises/{id}/tentar-de-novo")
    @Operation(summary = "Repete a etapa que falhou", description = "Só em FALHOU, com o texto já guardado. 202.")
    public ResponseEntity<AnaliseEditalResponse> tentarDeNovo(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.resposta(service.tentarDeNovo(id)));
    }

    @PostMapping("/analises/{id}/confirmar")
    @Operation(summary = "Confirma a proposta revisada", description = "D-62: a única escrita no cadastro. Cria o que é novo, aplica a cobertura (D-60) e ativa o concurso.")
    public ConcursoResponse confirmar(@PathVariable Long id, @RequestBody ConfirmacaoEditalRequest corpo) {
        return concursoService.resposta(service.confirmar(id, corpo).getId());
    }
}
