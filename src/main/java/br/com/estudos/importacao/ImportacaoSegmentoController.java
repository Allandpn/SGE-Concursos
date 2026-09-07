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

/** Endpoints de importação/exportação de segmentos (docs/SPRINT-10-SEGMENTO.md §5). */
@RestController
@RequestMapping("/api/segmentos")
@Tag(name = "Importação/Exportação de Segmentos", description = "CSV de segmentos de material — layout em docs/SPRINT-10-SEGMENTO.md §4/§5")
public class ImportacaoSegmentoController {

    private final ImportacaoSegmentoService importacaoSegmentoService;
    private final ExportacaoSegmentoService exportacaoSegmentoService;

    public ImportacaoSegmentoController(
            ImportacaoSegmentoService importacaoSegmentoService,
            ExportacaoSegmentoService exportacaoSegmentoService) {
        this.importacaoSegmentoService = importacaoSegmentoService;
        this.exportacaoSegmentoService = exportacaoSegmentoService;
    }

    @PostMapping("/importacoes/validar")
    @Operation(summary = "Valida um CSV de segmentos sem gravar nada", description = "Mesma checagem de /confirmar, só que não persiste.")
    public ResumoImportacaoSegmento validar(@RequestParam("arquivo") MultipartFile arquivo) {
        return importacaoSegmentoService.validar(arquivo);
    }

    @PostMapping("/importacoes/confirmar")
    @Operation(summary = "Importa um CSV de segmentos", description = "Tudo ou nada: qualquer linha recusada rejeita o arquivo inteiro. Reimportar a mesma chaveExternaSegmento atualiza, nunca duplica.")
    public ResumoImportacaoSegmento confirmar(@RequestParam("arquivo") MultipartFile arquivo) {
        return importacaoSegmentoService.confirmar(arquivo);
    }

    @GetMapping(value = "/exportacao", produces = "text/csv")
    @Operation(summary = "Exporta todos os segmentos em CSV", description = "Mesmo layout aceito por /importacoes/confirmar — round-trip via chaveExternaSegmento (D-53).")
    public ResponseEntity<String> exportar() {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(exportacaoSegmentoService.exportar());
    }
}
