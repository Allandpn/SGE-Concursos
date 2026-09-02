package br.com.estudos.importacao;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Endpoints de importação/exportação de assunto (docs/SPRINT-2-CADASTRO.md §4). */
@RestController
@RequestMapping("/api/assuntos")
@Tag(name = "Importação/Exportação", description = "CSV de assunto — layout em http/README.md (docs/SPRINT-2-CADASTRO.md §5)")
public class ImportacaoController {

    private final ImportacaoAssuntoService importacaoAssuntoService;
    private final ExportacaoAssuntoService exportacaoAssuntoService;

    public ImportacaoController(
            ImportacaoAssuntoService importacaoAssuntoService,
            ExportacaoAssuntoService exportacaoAssuntoService) {
        this.importacaoAssuntoService = importacaoAssuntoService;
        this.exportacaoAssuntoService = exportacaoAssuntoService;
    }

    @PostMapping("/importacoes/validar")
    @Operation(summary = "Valida um CSV de assuntos sem gravar nada", description = "Mesma checagem de /confirmar, só que não persiste — usa pra conferir antes de importar de verdade.")
    public ResumoImportacao validar(@RequestParam("arquivo") MultipartFile arquivo) {
        return importacaoAssuntoService.validar(arquivo);
    }

    @PostMapping("/importacoes/confirmar")
    @Operation(summary = "Importa um CSV de assuntos", description = "Tudo ou nada: qualquer linha recusada rejeita o arquivo inteiro, nada é gravado.")
    public ResumoImportacao confirmar(@RequestParam("arquivo") MultipartFile arquivo) {
        return importacaoAssuntoService.confirmar(arquivo);
    }

    @GetMapping(value = "/exportacao", produces = "text/csv")
    @Operation(summary = "Exporta todos os assuntos em CSV", description = "Mesmo layout aceito por /importacoes/confirmar.")
    public ResponseEntity<String> exportar() {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(exportacaoAssuntoService.exportar());
    }
}
