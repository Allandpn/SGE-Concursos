package br.com.estudos.importacao;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;

/**
 * Exportação de assunto por CSV, mesmo formato de entrada, com `id`
 * preenchido (docs/SPRINT-2-CADASTRO.md §6). Assunto arquivado não entra —
 * {@link AssuntoRepository#listarParaExportacao} já filtra por `ativo`.
 */
@Service
public class ExportacaoAssuntoService {

    private static final CSVFormat FORMATO = CSVFormat.DEFAULT.builder()
        .setHeader("id", "disciplina", "assunto", "peso", "ordem", "dificuldadePercebida")
        .build();

    private final AssuntoRepository assuntoRepository;

    public ExportacaoAssuntoService(AssuntoRepository assuntoRepository) {
        this.assuntoRepository = assuntoRepository;
    }

    @Transactional(readOnly = true)
    public String exportar() {
        var texto = new StringWriter();
        try (var impressor = new CSVPrinter(texto, FORMATO)) {
            for (var assunto : assuntoRepository.listarParaExportacao()) {
                impressor.printRecord(
                    assunto.getId(),
                    assunto.getDisciplina().getNome(),
                    assunto.getNome(),
                    assunto.getPeso(),
                    assunto.getOrdem(),
                    assunto.getDificuldadePercebida()
                );
            }
        } catch (IOException e) {
            // StringWriter não lança IOException de verdade — CSVPrinter só declara
            // a exceção porque implementa Closeable/Appendable em geral.
            throw new IllegalStateException("Falha ao montar CSV de exportação.", e);
        }
        return texto.toString();
    }
}
