package br.com.estudos.importacao;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.segmento.SegmentoRequest;
import br.com.estudos.segmento.SegmentoService;
import br.com.estudos.shared.exception.DominioException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Importação de segmentos por CSV — segunda importação de J-1
 * (`02_JORNADAS.md` §J-1, "Segunda importação"; `docs/SPRINT-10-SEGMENTO.md`
 * §4). Mesmo mecanismo `validar`/`confirmar` de {@link ImportacaoAssuntoService}
 * — tudo-ou-nada, resumo antes de confirmar — mas com uma assimetria real:
 * o CSV de segmentos não tem coluna `id`, então `chaveExternaAssunto` sem
 * correspondência é linha recusada comum, nunca aborta o arquivo inteiro na
 * hora (diferente de `id` de assunto inexistente em ImportacaoAssuntoService).
 */
@Service
public class ImportacaoSegmentoService {

    private final AssuntoRepository assuntoRepository;
    private final SegmentoService segmentoService;

    public ImportacaoSegmentoService(AssuntoRepository assuntoRepository, SegmentoService segmentoService) {
        this.assuntoRepository = assuntoRepository;
        this.segmentoService = segmentoService;
    }

    /** Lê e resolve o arquivo, mas nunca grava — força rollback no final. */
    @Transactional
    public ResumoImportacaoSegmento validar(MultipartFile arquivo) {
        var resumo = processar(arquivo);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return resumo;
    }

    /** Só grava se nada foi recusado (regra 1, tudo ou nada). */
    @Transactional
    public ResumoImportacaoSegmento confirmar(MultipartFile arquivo) {
        var resumo = processar(arquivo);
        if (!resumo.recusadas().isEmpty()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return resumo;
    }

    private ResumoImportacaoSegmento processar(MultipartFile arquivo) {
        var recusadas = new ArrayList<LinhaRecusada>();
        int segmentosNovos = 0;
        int segmentosAtualizados = 0;

        for (var linha : lerLinhas(arquivo)) {
            int numeroLinha = (int) linha.getRecordNumber() + 1; // +1 pelo cabeçalho

            var chaveExternaSegmento = valor(linha, "chaveExternaSegmento");
            if (chaveExternaSegmento == null) {
                recusadas.add(new LinhaRecusada(numeroLinha, "chaveExternaSegmento em branco"));
                continue;
            }

            var chaveExternaAssunto = valor(linha, "chaveExternaAssunto");
            if (chaveExternaAssunto == null) {
                recusadas.add(new LinhaRecusada(numeroLinha, "chaveExternaAssunto em branco"));
                continue;
            }

            var assunto = assuntoRepository.findByChaveExterna(chaveExternaAssunto);
            if (assunto.isEmpty()) {
                // Diferente de ImportacaoAssuntoService: sem coluna `id` aqui, não
                // há "arquivo corrompido" possível — só uma linha apontando pra um
                // assunto que ainda não existe. Recusa a linha, segue processando.
                recusadas.add(new LinhaRecusada(numeroLinha, "assunto inexistente para a chave externa: " + chaveExternaAssunto));
                continue;
            }

            var ordem = lerInteiro(linha, "ordem", numeroLinha, recusadas);
            if (ordem == null) continue;

            var arquivoMaterial = valor(linha, "arquivo");
            if (arquivoMaterial == null) {
                recusadas.add(new LinhaRecusada(numeroLinha, "arquivo em branco"));
                continue;
            }

            var recusadasAntes = recusadas.size();
            var paginaInicial = lerInteiroOpcional(linha, "paginaInicial", numeroLinha, recusadas);
            var paginaFinal = lerInteiroOpcional(linha, "paginaFinal", numeroLinha, recusadas);
            var tempoEstimadoMin = lerInteiroOpcional(linha, "tempoEstimadoMin", numeroLinha, recusadas);
            // Um dos três opcionais tinha valor mas não parseou — linha já foi
            // recusada dentro de lerInteiroOpcional, não processa o resto dela.
            if (recusadas.size() > recusadasAntes) continue;

            var request = new SegmentoRequest(
                assunto.get().getId(), chaveExternaSegmento, ordem, arquivoMaterial,
                paginaInicial, paginaFinal, tempoEstimadoMin);

            var jaExistia = segmentoService.buscarPorChaveExterna(chaveExternaSegmento).isPresent();
            try {
                segmentoService.criarOuAtualizar(request);
                if (jaExistia) segmentosAtualizados++; else segmentosNovos++;
            } catch (DominioException e) {
                recusadas.add(new LinhaRecusada(numeroLinha, e.getMessage()));
            }
        }

        return new ResumoImportacaoSegmento(segmentosNovos, segmentosAtualizados, recusadas);
    }

    private List<CSVRecord> lerLinhas(MultipartFile arquivo) {
        try (var leitor = new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8);
             var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(leitor)) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new ValidationException("Não foi possível ler o arquivo CSV.", "ARQUIVO_ILEGIVEL", null);
        }
    }

    private Integer lerInteiro(CSVRecord linha, String coluna, int numeroLinha, List<LinhaRecusada> recusadas) {
        var texto = valor(linha, coluna);
        if (texto == null) {
            recusadas.add(new LinhaRecusada(numeroLinha, coluna + " em branco"));
            return null;
        }
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException e) {
            recusadas.add(new LinhaRecusada(numeroLinha, coluna + " inválido: " + texto));
            return null;
        }
    }

    private Integer lerInteiroOpcional(CSVRecord linha, String coluna, int numeroLinha, List<LinhaRecusada> recusadas) {
        var texto = valor(linha, coluna);
        if (texto == null) return null;
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException e) {
            recusadas.add(new LinhaRecusada(numeroLinha, coluna + " inválido: " + texto));
            return null;
        }
    }

    /** Célula em branco e coluna ausente do cabeçalho contam como "sem valor". */
    private static String valor(CSVRecord linha, String coluna) {
        if (!linha.isMapped(coluna)) return null;
        var bruto = linha.get(coluna);
        if (bruto == null) return null;
        var aparado = bruto.trim();
        return aparado.isEmpty() ? null : aparado;
    }
}
