package br.com.estudos.importacao;

import java.io.IOException;
import java.io.StringWriter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.segmento.SegmentoRepository;

/**
 * Exportação de segmento por CSV, mesmo layout de entrada
 * (docs/SPRINT-10-SEGMENTO.md §5) — round-trip só é possível porque
 * `chaveExternaSegmento` (D-53) já dá identidade estável, mesmo papel que
 * `id` cumpre em {@link ExportacaoAssuntoService}. Sem filtro de arquivado —
 * `Segmento` não tem esse conceito (§1.2 do doc técnico).
 */
@Service
public class ExportacaoSegmentoService {

    private static final CSVFormat FORMATO = CSVFormat.DEFAULT.builder()
        .setHeader("chaveExternaSegmento", "chaveExternaAssunto", "ordem", "arquivo",
            "paginaInicial", "paginaFinal", "tempoEstimadoMin")
        .build();

    private final SegmentoRepository segmentoRepository;

    public ExportacaoSegmentoService(SegmentoRepository segmentoRepository) {
        this.segmentoRepository = segmentoRepository;
    }

    @Transactional(readOnly = true)
    public String exportar() {
        var texto = new StringWriter();
        try (var impressor = new CSVPrinter(texto, FORMATO)) {
            for (var segmento : segmentoRepository.listarParaExportacao()) {
                impressor.printRecord(
                    segmento.getChaveExterna(),
                    segmento.getAssunto().getChaveExterna(),
                    segmento.getOrdem(),
                    segmento.getArquivo(),
                    segmento.getPaginaInicial(),
                    segmento.getPaginaFinal(),
                    segmento.getTempoEstimadoMin()
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
