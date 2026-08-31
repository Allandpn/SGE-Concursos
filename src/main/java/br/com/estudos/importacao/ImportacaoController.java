package br.com.estudos.importacao;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Endpoints de importação/exportação de assunto (docs/SPRINT-2-CADASTRO.md §4). */
@RestController
@RequestMapping("/api/assuntos")
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
    public ResumoImportacao validar(@RequestParam("arquivo") MultipartFile arquivo) {
        return importacaoAssuntoService.validar(arquivo);
    }

    @PostMapping("/importacoes/confirmar")
    public ResumoImportacao confirmar(@RequestParam("arquivo") MultipartFile arquivo) {
        return importacaoAssuntoService.confirmar(arquivo);
    }

    @GetMapping(value = "/exportacao", produces = "text/csv")
    public ResponseEntity<String> exportar() {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(exportacaoAssuntoService.exportar());
    }
}
