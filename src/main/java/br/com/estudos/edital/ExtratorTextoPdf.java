package br.com.estudos.edital;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.estudos.shared.exception.ValidationException;

/** Texto do PDF do edital (ADR-043, docs/SPRINT-18-CONCURSOS.md §4.2). O PDF em si não é guardado. */
@Component
public class ExtratorTextoPdf {

    private static final Logger log = LoggerFactory.getLogger(ExtratorTextoPdf.class);

    static final int MINIMO_UTIL = 500;
    static final int MAXIMO = 600_000;

    public String extrair(byte[] pdf) {
        String texto;
        try (var documento = Loader.loadPDF(pdf)) {
            var leitor = new PDFTextStripper();
            leitor.setSortByPosition(true);
            texto = leitor.getText(documento);
        } catch (InvalidPasswordException e) {
            throw new ValidationException("O PDF está protegido por senha.", "PDF_PROTEGIDO", "arquivo");
        } catch (IOException e) {
            throw new ValidationException("O arquivo não é um PDF válido.", "ARQUIVO_NAO_PDF", "arquivo");
        }
        if (texto.replaceAll("\\s+", "").length() < MINIMO_UTIL) {
            throw new ValidationException(
                "Este PDF é imagem (escaneado) e não tem texto para ler. Envie a versão com texto do edital.",
                "PDF_SEM_TEXTO", "arquivo");
        }
        if (texto.length() > MAXIMO) {
            log.warn("Texto do edital com {} caracteres cortado em {}", texto.length(), MAXIMO);
            texto = texto.substring(0, MAXIMO);
        }
        return texto;
    }
}
