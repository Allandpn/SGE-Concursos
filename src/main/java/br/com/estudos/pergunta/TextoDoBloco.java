package br.com.estudos.pergunta;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * O texto de um bloco, página a página, com `[página N]` antes de cada uma (docs/SPRINT-32-PERGUNTAS.md §4): N é a
 * numeração do PDF do bloco, a mesma do leitor, e é o que a IA devolve em cada ponto do gabarito (D-123).
 */
@Component
public class TextoDoBloco {

    /** Menos que isso de texto útil: PDF escaneado, sem perguntas (D-122). */
    static final int MINIMO_UTIL = 300;
    static final int MAXIMO = 60_000;

    public record Texto(String conteudo, int paginas) {}

    public static class SemArquivo extends RuntimeException {
        SemArquivo(String mensagem) {
            super(mensagem);
        }
    }

    private final Path diretorio;
    private final Path proprios;

    public TextoDoBloco(@Value("${estudos.material.dir:/dados/material}") String diretorio,
                        @Value("${estudos.editais.dir:/dados/editais}") String editais) {
        this.diretorio = Path.of(diretorio).toAbsolutePath().normalize();
        this.proprios = Path.of(editais, "proprios").toAbsolutePath().normalize(); // ADR-054
    }

    /** Vazio quando o bloco não tem texto que sirva. */
    public java.util.Optional<Texto> ler(String arquivo) {
        var caminho = resolver(arquivo);
        try (var documento = Loader.loadPDF(Files.readAllBytes(caminho))) {
            var leitor = new PDFTextStripper();
            leitor.setSortByPosition(true);
            var texto = new StringBuilder();
            int paginas = documento.getNumberOfPages();
            for (int p = 1; p <= paginas && texto.length() < MAXIMO; p++) {
                leitor.setStartPage(p);
                leitor.setEndPage(p);
                texto.append("[página ").append(p).append("]\n").append(leitor.getText(documento).strip()).append("\n\n");
            }
            var conteudo = texto.length() > MAXIMO ? texto.substring(0, MAXIMO) : texto.toString();
            var util = conteudo.replaceAll("\\[página \\d+]", "").replaceAll("\\s+", "").length();
            return util < MINIMO_UTIL ? java.util.Optional.empty() : java.util.Optional.of(new Texto(conteudo, paginas));
        } catch (IOException e) {
            throw new SemArquivo("O PDF do bloco não abriu: " + e.getMessage());
        }
    }

    /** `/material/blocos/x/segmento-01.pdf` → arquivo dentro de MATERIAL_DIR, sem sair dele. */
    Path resolver(String arquivo) {
        if (arquivo == null || !arquivo.startsWith("/material/")) {
            throw new SemArquivo("O bloco não é um PDF do servidor: " + arquivo);
        }
        var base = arquivo.startsWith("/material/proprios/") ? proprios : diretorio;
        var relativo = arquivo.startsWith("/material/proprios/") ? arquivo.substring("/material/proprios/".length()) : arquivo.substring("/material/".length());
        var caminho = base.resolve(relativo).normalize();
        if (!caminho.startsWith(base) || !Files.isRegularFile(caminho)) {
            throw new SemArquivo("Arquivo do bloco não encontrado: " + arquivo);
        }
        return caminho;
    }
}
