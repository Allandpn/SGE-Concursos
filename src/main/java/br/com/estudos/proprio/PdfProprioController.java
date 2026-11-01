package br.com.estudos.proprio;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** O PDF próprio (docs/SPRINT-33-PDF-PROPRIO.md §3; D-129 a D-132, ADR-054). */
@RestController
@Tag(name = "PDF próprio", description = "PDFs da pessoa ligados a um assunto, cortados em blocos pelo SGE (D-129 a D-132)")
public class PdfProprioController {

    private final PdfProprioService pdfs;

    public PdfProprioController(PdfProprioService pdfs) {
        this.pdfs = pdfs;
    }

    @PostMapping(value = "/api/assuntos/{id}/pdfs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adiciona um PDF ao assunto e o corta em blocos", description = "tipo: APOSTILA (~2 min/página) ou LEI_SECA (~3 min/página). Até 50 MB.")
    public PdfProprioService.Resultado enviar(@PathVariable long id, @RequestParam("arquivo") MultipartFile arquivo,
                                              @RequestParam(value = "tipo", required = false) String tipo) {
        return pdfs.enviar(id, arquivo, tipo);
    }

    @GetMapping("/api/assuntos/{id}/pdfs")
    @Operation(summary = "Os PDFs próprios do assunto, com os blocos de cada um")
    public List<PdfProprioService.PdfDoAssunto> listar(@PathVariable long id) {
        return pdfs.listar(id);
    }

    @PostMapping("/api/pdfs/{id}/tirar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Tira o PDF: arquiva os blocos dele de uma vez (D-132). Nada é apagado.")
    public void tirar(@PathVariable long id) {
        pdfs.tirar(id);
    }
}
