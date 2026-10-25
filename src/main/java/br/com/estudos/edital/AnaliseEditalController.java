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


@RestController
@RequestMapping("/api/editais")
@Tag(name = "Edital novo", description = "Edital novo: o SGE lê o cabeçalho; a conferência é do planejamento e da pessoa (ADR-043, ADR-047)")
public class AnaliseEditalController {

    private final AnaliseEditalService service;

    public AnaliseEditalController(AnaliseEditalService service) {
        this.service = service;
    }

    @GetMapping("/ia")
    @Operation(summary = "A análise por IA está disponível?", description = "Sem chave do OpenRouter, disponivel=false com o motivo.")
    public IaStatusResponse statusIa() {
        return service.statusIa();
    }

    @PostMapping(value = "/analises", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia o PDF do edital", description = "Guarda o PDF, extrai o texto e lê o cabeçalho (concurso e cargos) em segundo plano. 202.")
    public ResponseEntity<AnaliseEditalResponse> iniciar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.resposta(service.iniciar(arquivo)));
    }

    @GetMapping("/analises")
    @Operation(summary = "Análises recentes", description = "As 10 mais recentes, para retomar uma em andamento.")
    public List<AnaliseEditalResponse> recentes() {
        return service.recentes().stream().map(service::resposta).toList();
    }

    @GetMapping("/analises/{id}")
    @Operation(summary = "Estado de uma análise", description = "Estado da análise; a conferência tem rotas próprias (docs/SPRINT-25-CONFERENCIA-EDITAL.md §5).")
    public AnaliseEditalResponse buscar(@PathVariable Long id) {
        return service.resposta(service.buscar(id));
    }

    @PostMapping("/analises/{id}/cargo")
    @Operation(summary = "Escolhe o cargo", description = "Só em ESCOLHER_CARGO. Passa a SEPARANDO_ASSUNTOS: o planejamento pega a análise. 202.")
    public ResponseEntity<AnaliseEditalResponse> escolherCargo(@PathVariable Long id, @RequestBody EscolhaCargoRequest corpo) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.resposta(service.escolherCargo(id, corpo.cargo())));
    }

    @PostMapping("/analises/{id}/tentar-de-novo")
    @Operation(summary = "Repete a etapa que falhou", description = "Só em FALHOU. Repete o cabeçalho aqui, ou devolve a análise ao planejamento. 202.")
    public ResponseEntity<AnaliseEditalResponse> tentarDeNovo(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.resposta(service.tentarDeNovo(id)));
    }
}
