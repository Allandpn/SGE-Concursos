package br.com.estudos.importacao;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.concurso.ConcursoService;
import br.com.estudos.concurso.ItemCobertura;
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
    private final AssuntoRepository assuntoRepository;
    private final ConcursoService concursoService;
    private final JsonMapper jsonMapper;

    public ImportacaoAssuntoService(
            DisciplinaRepository disciplinaRepository,
            DisciplinaService disciplinaService,
            AssuntoService assuntoService,
            AssuntoRepository assuntoRepository,
            ConcursoService concursoService,
            JsonMapper jsonMapper) {
        this.disciplinaRepository = disciplinaRepository;
        this.disciplinaService = disciplinaService;
        this.assuntoService = assuntoService;
        this.assuntoRepository = assuntoRepository;
        this.concursoService = concursoService;
        this.jsonMapper = jsonMapper;
    }

    /** Lê e resolve o arquivo, mas nunca grava — força rollback no final (§5.1). */
    @Transactional
    public ResumoImportacao validar(MultipartFile arquivo) {
        var resumo = processar(arquivo).resumo();
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
        return confirmar(arquivo, null);
    }

    /**
     * Com o `concurso.json` da pasta do planejamento (docs/SPRINT-18-CONCURSOS.md §5,
     * ADR-042): sem linha recusada, registra (ou reencontra pelo slug) o
     * concurso, grava a cobertura com os assuntos do arquivo e o ativa.
     * concurso.json inválido desfaz o arquivo inteiro — tudo ou nada (regra 1).
     */
    @Transactional
    public ResumoImportacao confirmar(MultipartFile arquivo, MultipartFile concursoJson) {
        var resultado = processar(arquivo);
        if (!resultado.resumo().recusadas().isEmpty()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return resultado.resumo();
        }
        if (concursoJson != null && !concursoJson.isEmpty()) {
            var concurso = lerConcurso(concursoJson);
            var itens = resultado.tocados().stream()
                .filter(Assunto::isAtivo)
                .map(a -> new ItemCobertura(a, a.getDisciplina(), a.getPeso(), a.getOrdem()))
                .toList();
            concursoService.registrarDoPlanejamento(concurso.slug(), concurso.nome(), concurso.dataProva(), itens);
        }
        return resultado.resumo();
    }

    private record ConcursoPlanejamento(String slug, String nome, LocalDate dataProva) {}

    private ConcursoPlanejamento lerConcurso(MultipartFile arquivo) {
        try {
            var json = jsonMapper.readTree(arquivo.getBytes());
            var slug = texto(json.get("slug"));
            var nome = texto(json.get("nome"));
            if (slug == null || nome == null) {
                throw new ValidationException("concurso.json precisa de slug e nome.", "CONCURSO_JSON_INVALIDO", "concurso");
            }
            var dataTexto = texto(json.get("dataProva"));
            LocalDate dataProva = null;
            if (dataTexto != null) {
                try {
                    dataProva = LocalDate.parse(dataTexto);
                } catch (DateTimeParseException e) {
                    throw new ValidationException("dataProva inválida no concurso.json: " + dataTexto, "CONCURSO_JSON_INVALIDO", "dataProva");
                }
            }
            return new ConcursoPlanejamento(slug, nome, dataProva);
        } catch (IOException | tools.jackson.core.JacksonException e) {
            throw new ValidationException("Não foi possível ler o concurso.json.", "CONCURSO_JSON_INVALIDO", "concurso");
        }
    }

    private static String texto(tools.jackson.databind.JsonNode no) {
        if (no == null || no.isNull()) return null;
        var valor = no.asString().trim();
        return valor.isEmpty() ? null : valor;
    }

    private record Resultado(ResumoImportacao resumo, List<Assunto> tocados) {}

    private Resultado processar(MultipartFile arquivo) {
        var disciplinasPorNome = new HashMap<String, Disciplina>();
        var proximaOrdemPorDisciplina = new HashMap<Long, Integer>();
        var disciplinasNovas = new AtomicInteger();
        var recusadas = new ArrayList<LinhaRecusada>();
        var tocados = new LinkedHashSet<Assunto>();
        int assuntosNovos = 0;
        int assuntosAtualizados = 0;

        var linhas = lerLinhas(arquivo);
        var deslocados = liberarOrdensQueVaoMudar(linhas);

        for (var linha : linhas) {
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

            var chaveExterna = valor(linha, "chaveExterna");
            var request = new AssuntoRequest(disciplina.getId(), nomeAssunto, peso, dificuldade, ordem, chaveExterna);
            var idTexto = valor(linha, "id");

            try {
                // 02_JORNADAS J-1 (v1.6.0): sem id, a chaveExterna já conhecida
                // identifica o registro — é a reimportação vinda do planejamento,
                // que nunca conhece o id do SGE (docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §5.1).
                var existentePorChave = idTexto == null && chaveExterna != null
                    ? assuntoRepository.findByChaveExterna(chaveExterna)
                    : Optional.<Assunto>empty();
                // 02_JORNADAS J-1 (v1.9.0): chave ainda desconhecida, mas o assunto
                // já existe na disciplina sem chave e com o mesmo nome — criado pela
                // análise de edital (J-5). Adota em vez de recusar por nome duplicado.
                var adotavel = existentePorChave.isEmpty() && idTexto == null && chaveExterna != null
                    ? buscarAdotavel(disciplina.getId(), nomeAssunto)
                    : Optional.<Assunto>empty();
                if (existentePorChave.isPresent()) {
                    tocados.add(assuntoService.atualizar(existentePorChave.get().getId(), request));
                    assuntosAtualizados++;
                } else if (adotavel.isPresent()) {
                    tocados.add(assuntoService.atualizar(adotavel.get().getId(), request));
                    assuntosAtualizados++;
                } else if (idTexto == null) {
                    tocados.add(assuntoService.criar(request));
                    assuntosNovos++;
                } else {
                    tocados.add(assuntoService.atualizar(parseId(idTexto, numeroLinha), request));
                    assuntosAtualizados++;
                }
            } catch (NotFoundException e) {
                // id de assunto inexistente é sinal de arquivo corrompido — recusa
                // o arquivo inteiro na hora, sem processar o resto (§5.1).
                return new Resultado(
                    new ResumoImportacao(0, 0, 0, List.of(new LinhaRecusada(numeroLinha, e.getMessage()))), List.of());
            } catch (DominioException e) {
                recusadas.add(new LinhaRecusada(numeroLinha, e.getMessage()));
            }
        }

        reposicionarDeslocados(deslocados);
        return new Resultado(
            new ResumoImportacao(disciplinasNovas.get(), assuntosNovos, assuntosAtualizados, recusadas),
            List.copyOf(tocados));
    }

    /**
     * D-48 (ordem única entre os ativos da disciplina) é verificada linha a
     * linha, então reordenar assuntos já existentes — trocar 1↔2, o caso comum
     * ao mudar de concurso-alvo (ADR-039) — colidiria no meio do arquivo.
     * Antes de processar, todo assunto existente cuja ordem vai mudar recebe
     * uma ordem provisória única (−id); a linha dele grava a definitiva logo
     * depois. Recusa em qualquer linha desfaz tudo, provisórias inclusive
     * (tudo ou nada, regra 1).
     */
    private List<Assunto> liberarOrdensQueVaoMudar(List<CSVRecord> linhas) {
        var mudando = new ArrayList<Assunto>();
        var referenciados = new java.util.HashSet<Long>();
        var ordensPedidas = new java.util.HashMap<Long, java.util.Set<Integer>>();
        for (var linha : linhas) {
            var ordemTexto = valor(linha, "ordem");
            var existente = buscarExistente(linha);
            existente.ifPresent(a -> referenciados.add(a.getId()));
            if (ordemTexto == null) continue;
            Integer ordemNova;
            try {
                ordemNova = Integer.valueOf(ordemTexto);
            } catch (NumberFormatException e) {
                continue; // a passada principal recusa a linha
            }
            var nomeDisciplina = valor(linha, "disciplina");
            if (nomeDisciplina != null) {
                disciplinaRepository.findByNomeIgnoreCase(nomeDisciplina)
                    .ifPresent(d -> ordensPedidas.computeIfAbsent(d.getId(), k -> new java.util.HashSet<>()).add(ordemNova));
            }
            if (existente.isPresent() && !ordemNova.equals(existente.get().getOrdem())) {
                existente.get().setOrdem(Math.toIntExact(-existente.get().getId()));
                mudando.add(existente.get());
            }
        }
        // Sprint 18 (02_JORNADAS J-1 v1.9.0): assunto que NÃO está no arquivo mas
        // ocupa a ordem que uma linha pede — o caso comum ao trocar de edital —
        // sai do caminho agora e vai para o fim da disciplina depois.
        var deslocados = new ArrayList<Assunto>();
        ordensPedidas.forEach((disciplinaId, ordens) ->
            assuntoRepository.findByDisciplinaIdAndAtivoTrue(disciplinaId).stream()
                .filter(a -> !referenciados.contains(a.getId()) && a.getOrdem() != null && ordens.contains(a.getOrdem()))
                .sorted(java.util.Comparator.comparing(Assunto::getOrdem))
                .forEach(a -> {
                    a.setOrdem(Math.toIntExact(-a.getId()));
                    mudando.add(a);
                    deslocados.add(a);
                }));
        if (!mudando.isEmpty()) {
            assuntoRepository.saveAllAndFlush(mudando);
        }
        return deslocados;
    }

    /** Os deslocados por {@link #liberarOrdensQueVaoMudar} vão para depois do último da disciplina, na ordem em que estavam. */
    private void reposicionarDeslocados(List<Assunto> deslocados) {
        for (var assunto : deslocados) {
            int maior = assuntoRepository.findByDisciplinaIdAndAtivoTrue(assunto.getDisciplina().getId()).stream()
                .map(Assunto::getOrdem)
                .filter(o -> o != null && o > 0)
                .max(Integer::compare)
                .orElse(0);
            assunto.setOrdem(maior + 1);
            assuntoRepository.saveAndFlush(assunto);
        }
    }

    private Optional<Assunto> buscarExistente(CSVRecord linha) {
        var idTexto = valor(linha, "id");
        if (idTexto != null) {
            try {
                return assuntoRepository.findById(Long.valueOf(idTexto));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        var chaveExterna = valor(linha, "chaveExterna");
        if (chaveExterna == null) return Optional.empty();
        var porChave = assuntoRepository.findByChaveExterna(chaveExterna);
        if (porChave.isPresent()) return porChave;
        var nomeDisciplina = valor(linha, "disciplina");
        var nomeAssunto = valor(linha, "assunto");
        if (nomeDisciplina == null || nomeAssunto == null) return Optional.empty();
        return disciplinaRepository.findByNomeIgnoreCase(nomeDisciplina)
            .flatMap(d -> buscarAdotavel(d.getId(), nomeAssunto));
    }

    /** Assunto ativo da disciplina, sem chave externa, de mesmo nome sem acento e sem caixa (J-1 v1.9.0). */
    private Optional<Assunto> buscarAdotavel(Long disciplinaId, String nomeAssunto) {
        var alvo = normalizar(nomeAssunto);
        return assuntoRepository.findByDisciplinaIdAndAtivoTrue(disciplinaId).stream()
            .filter(a -> a.getChaveExterna() == null && normalizar(a.getNome()).equals(alvo))
            .findFirst();
    }

    /** Mesma comparação de ux_assunto_j1_nome_por_disciplina: sem acento, sem caixa. */
    static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase()
            .trim();
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
