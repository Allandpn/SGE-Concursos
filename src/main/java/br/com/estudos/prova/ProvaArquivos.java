package br.com.estudos.prova;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Os arquivos da prova no volume {@code provas} (docs/SPRINT-24-PROVA-GABARITO.md §2.5):
 * {@code entrada/<id>/} é do SGE (o worker lê); {@code paginas/<id>/} é do worker (o SGE serve
 * ao usuário, D-85). Cada lado escreve só no seu subdiretório.
 */
@Component
public class ProvaArquivos {

    private final Path raiz;

    public ProvaArquivos(@Value("${estudos.provas.dir:/dados/provas}") String diretorio) {
        this.raiz = Path.of(diretorio).toAbsolutePath().normalize();
    }

    /** Grava um arquivo de entrada; o nome é fixo, nunca vem do usuário. */
    public void salvarEntrada(Long provaId, String nome, byte[] bytes) {
        try {
            var dir = raiz.resolve("entrada").resolve(provaId.toString());
            Files.createDirectories(dir);
            Files.write(dir.resolve(nome), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gravar " + nome + " da prova " + provaId, e);
        }
    }

    /** A imagem da página {@code n} (D-85); nunca sai de {@code paginas/<id>/}. */
    public Optional<Path> pagina(Long provaId, int n) {
        if (n < 1) return Optional.empty();
        var dir = raiz.resolve("paginas").resolve(provaId.toString()).normalize();
        for (var extensao : new String[] {"jpg", "png"}) {
            var arquivo = dir.resolve(String.format("%02d.%s", n, extensao)).normalize();
            if (arquivo.startsWith(dir) && Files.isRegularFile(arquivo)) return Optional.of(arquivo);
        }
        return Optional.empty();
    }
}
