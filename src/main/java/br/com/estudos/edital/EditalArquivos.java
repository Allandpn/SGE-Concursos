package br.com.estudos.edital;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * O PDF do edital no volume {@code editais} (docs/SPRINT-25-CONFERENCIA-EDITAL.md §2.5): o SGE escreve
 * {@code <analise_id>/edital.pdf} ao receber o envio; o planejamento só lê. Sai quando a conferência é
 * concluída. O nome é fixo, nunca vem do usuário.
 */
@Component
public class EditalArquivos {

    static final String NOME = "edital.pdf";

    private final Path raiz;

    public EditalArquivos(@Value("${estudos.editais.dir:/dados/editais}") String diretorio) {
        this.raiz = Path.of(diretorio).toAbsolutePath().normalize();
    }

    /** Grava o PDF e devolve o caminho relativo à raiz do volume (o que o worker recebe). */
    public String salvarPdf(Long analiseId, byte[] bytes) {
        try {
            var dir = raiz.resolve(analiseId.toString());
            Files.createDirectories(dir);
            Files.write(dir.resolve(NOME), bytes);
            return analiseId + "/" + NOME;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível guardar o PDF da análise " + analiseId, e);
        }
    }

    /** Tira o PDF e a pasta do volume; já não existir não é erro. */
    public void apagar(Long analiseId) {
        try {
            var dir = raiz.resolve(analiseId.toString()).normalize();
            if (!dir.startsWith(raiz)) return;
            Files.deleteIfExists(dir.resolve(NOME));
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            // o arquivo some na próxima limpeza; a conclusão não pode falhar por isso
        }
    }

    Path raiz() {
        return raiz;
    }
}
