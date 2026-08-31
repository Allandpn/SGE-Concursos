package br.com.estudos.importacao;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.exception.DominioException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Importação de assuntos por CSV (J-1, `02_JORNADAS.md` §J-1,
 * `docs/SPRINT-2-CADASTRO.md` §5). `validar` e `confirmar` leem e resolvem o
 * arquivo do mesmo jeito (§5.1) — reaproveita {@link DisciplinaService} e
 * {@link AssuntoService} em vez de duplicar criação/tradução de erro; a
 * diferença entre os dois é só se a transação commita no final ou é forçada
 * a rollback.
 */
@Service
public class ImportacaoAssuntoService {

    private final DisciplinaRepository disciplinaRepository;
    private final DisciplinaService disciplinaService;
    private final AssuntoService assuntoService;

    public ImportacaoAssuntoService(
            DisciplinaRepository disciplinaRepository,
            DisciplinaService disciplinaService,
            AssuntoService assuntoService) {
        this.disciplinaRepository = disciplinaRepository;
        this.disciplinaService = disciplinaService;
        this.assuntoService = assuntoService;
    }

    /** Lê e resolve o arquivo, mas nunca grava — força rollback no final (§5.1). */
    @Transactional
    public ResumoImportacao validar(MultipartFile arquivo) {
        var resumo = processar(arquivo);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return resumo;
    }

    /**
     * Revalida do zero e grava — mas só se nada foi recusado (regra 1,
     * tudo ou nada). Não confia no resumo que `validar` devolveu (§5.2):
     * entre as duas chamadas o estado pode ter mudado, então o trabalho é
     * refeito aqui, não reaproveitado.
     */
    @Transactional
    public ResumoImportacao confirmar(MultipartFile arquivo) {
        var resumo = processar(arquivo);
        if (!resumo.recusadas().isEmpty()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return resumo;
    }

    private ResumoImportacao processar(MultipartFile arquivo) {
        var disciplinasPorNome = new HashMap<String, Disciplina>();
        var proximaOrdemPorDisciplina = new HashMap<Long, Integer>();
        var disciplinasNovas = new AtomicInteger();
        var recusadas = new ArrayList<LinhaRecusada>();
        int assuntosNovos = 0;
        int assuntosAtualizados = 0;

        for (var linha : lerLinhas(arquivo)) {
            int numeroLinha = (int) linha.getRecordNumber() + 1; // +1 pelo cabeçalho

            var nomeDisciplina = valor(linha, "disciplina");
            var nomeAssunto = valor(linha, "assunto");
            if (nomeDisciplina == null) {
                recusadas.add(new LinhaRecusada(numeroLinha, "disciplina em branco"));
                continue;
            }
            if (nomeAssunto == null) {
                recusadas.add(new LinhaRecusada(numeroLinha, "assunto em branco"));
                continue;
            }

            var peso = lerPeso(linha, numeroLinha, recusadas);
            if (peso == null) continue;

            var dificuldade = lerDificuldade(linha, numeroLinha, recusadas);
            if (dificuldade == null) continue;

            var disciplina = disciplinasPorNome.computeIfAbsent(nomeDisciplina.toLowerCase(), chave -> {
                var existente = disciplinaRepository.findByNomeIgnoreCase(nomeDisciplina);
                if (existente.isPresent()) return existente.get();
                disciplinasNovas.incrementAndGet();
                // Sem coluna de peso de disciplina no CSV (02_JORNADAS §J-1) — MEDIO fixo.
                return disciplinaService.criar(new DisciplinaRequest(nomeDisciplina, TipoPeso.MEDIO));
            });

            var ordem = lerOrdem(linha, numeroLinha, disciplina.getId(), proximaOrdemPorDisciplina, recusadas);
            if (ordem == null) continue;

            var request = new AssuntoRequest(disciplina.getId(), nomeAssunto, peso, dificuldade, ordem);
            var idTexto = valor(linha, "id");

            try {
                if (idTexto == null) {
                    assuntoService.criar(request);
                    assuntosNovos++;
                } else {
                    assuntoService.atualizar(parseId(idTexto, numeroLinha), request);
                    assuntosAtualizados++;
                }
            } catch (NotFoundException e) {
                // id de assunto inexistente é sinal de arquivo corrompido — recusa
                // o arquivo inteiro na hora, sem processar o resto (§5.1).
                return new ResumoImportacao(0, 0, 0, List.of(new LinhaRecusada(numeroLinha, e.getMessage())));
            } catch (DominioException e) {
                recusadas.add(new LinhaRecusada(numeroLinha, e.getMessage()));
            }
        }

        return new ResumoImportacao(disciplinasNovas.get(), assuntosNovos, assuntosAtualizados, recusadas);
    }

    private List<CSVRecord> lerLinhas(MultipartFile arquivo) {
        try (var leitor = new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8);
             var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(leitor)) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new ValidationException("Não foi possível ler o arquivo CSV.", "ARQUIVO_ILEGIVEL", null);
        }
    }

    private TipoPeso lerPeso(CSVRecord linha, int numeroLinha, List<LinhaRecusada> recusadas) {
        var texto = valor(linha, "peso");
        if (texto == null) return TipoPeso.MEDIO;
        try {
            return TipoPeso.valueOf(texto.toUpperCase());
        } catch (IllegalArgumentException e) {
            recusadas.add(new LinhaRecusada(numeroLinha, "peso inválido: " + texto));
            return null;
        }
    }

    private Short lerDificuldade(CSVRecord linha, int numeroLinha, List<LinhaRecusada> recusadas) {
        var texto = valor(linha, "dificuldadePercebida");
        if (texto == null) return 3;
        try {
            return Short.valueOf(texto);
        } catch (NumberFormatException e) {
            recusadas.add(new LinhaRecusada(numeroLinha, "dificuldadePercebida inválida: " + texto));
            return null;
        }
    }

    private Integer lerOrdem(
            CSVRecord linha, int numeroLinha, Long disciplinaId,
            java.util.Map<Long, Integer> proximaOrdemPorDisciplina, List<LinhaRecusada> recusadas) {
        var texto = valor(linha, "ordem");
        if (texto == null) return proximaOrdemPorDisciplina.merge(disciplinaId, 1, Integer::sum);
        try {
            return Integer.valueOf(texto);
        } catch (NumberFormatException e) {
            recusadas.add(new LinhaRecusada(numeroLinha, "ordem inválida: " + texto));
            return null;
        }
    }

    private Long parseId(String idTexto, int numeroLinha) {
        try {
            return Long.valueOf(idTexto);
        } catch (NumberFormatException e) {
            throw new NotFoundException("id inválido: " + idTexto, "ASSUNTO_INEXISTENTE");
        }
    }

    /** Célula em branco e coluna ausente do cabeçalho contam como "sem valor" — coluna é opcional em ambos os casos. */
    private static String valor(CSVRecord linha, String coluna) {
        if (!linha.isMapped(coluna)) return null;
        var bruto = linha.get(coluna);
        if (bruto == null) return null;
        var aparado = bruto.trim();
        return aparado.isEmpty() ? null : aparado;
    }
}
