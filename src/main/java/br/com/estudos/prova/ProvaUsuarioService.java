package br.com.estudos.prova;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.concurso.CoberturaEdital;
import br.com.estudos.concurso.CoberturaEditalRepository;
import br.com.estudos.concurso.ConcursoRepository;
import br.com.estudos.prova.ProvaInterna.AlternativaLida;
import br.com.estudos.prova.ProvaPublica.Amostra;
import br.com.estudos.prova.ProvaPublica.AssuntoFurado;
import br.com.estudos.prova.ProvaPublica.AssuntoSemQuestao;
import br.com.estudos.prova.ProvaPublica.Cobertura;
import br.com.estudos.prova.ProvaPublica.CorrecaoRequest;
import br.com.estudos.prova.ProvaPublica.Declaradas;
import br.com.estudos.prova.ProvaPublica.Detalhe;
import br.com.estudos.prova.ProvaPublica.Duvidosa;
import br.com.estudos.prova.ProvaPublica.Envio;
import br.com.estudos.prova.ProvaPublica.PorDisciplina;
import br.com.estudos.prova.ProvaPublica.QuestaoRelatorio;
import br.com.estudos.prova.ProvaPublica.Reanalise;
import br.com.estudos.prova.ProvaPublica.Relatorio;
import br.com.estudos.prova.ProvaPublica.Resumo;
import br.com.estudos.shared.enums.AtribuicaoQuestao;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.MotivoNaoAvaliavel;
import br.com.estudos.shared.enums.StatusProva;
import br.com.estudos.shared.enums.VereditoCobertura;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * A prova do lado do usuário (J-6; docs/SPRINT-24-PROVA-GABARITO.md §4, §6): envio, conferência,
 * confirmação, reanálise e relatório. Quem lê o PDF e julga é o planejamento (ProvaService);
 * aqui o usuário só carrega, confere e acompanha. Tudo que o relatório mostra é derivado (D-16).
 */
@Service
public class ProvaUsuarioService {

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final int MAX_FALTAS = 5;
    private static final int TRECHO = 140;

    private final ProvaRepository provaRepository;
    private final QuestaoRepository questaoRepository;
    private final VereditoQuestaoRepository vereditoRepository;
    private final ConcursoRepository concursoRepository;
    private final CoberturaEditalRepository coberturaRepository;
    private final ProvaService provaService;
    private final ProvaArquivos arquivos;

    public ProvaUsuarioService(
            ProvaRepository provaRepository,
            QuestaoRepository questaoRepository,
            VereditoQuestaoRepository vereditoRepository,
            ConcursoRepository concursoRepository,
            CoberturaEditalRepository coberturaRepository,
            ProvaService provaService,
            ProvaArquivos arquivos) {
        this.provaRepository = provaRepository;
        this.questaoRepository = questaoRepository;
        this.vereditoRepository = vereditoRepository;
        this.concursoRepository = concursoRepository;
        this.coberturaRepository = coberturaRepository;
        this.provaService = provaService;
        this.arquivos = arquivos;
    }

    // ------------------------------------------------------------ envio (J-6 passos 1-2)

    /**
     * Recebe a prova e, se houver, o gabarito (PDF ou texto). A mesma prova ao mesmo concurso é
     * uma só: reenviar devolve a existente, sem regravar nada (idempotência, como D-45).
     */
    @Transactional
    public Envio enviar(MultipartFile prova, MultipartFile gabarito, String gabaritoTexto, String tipo) {
        var alvo = concursoRepository.findByAtivoTrue()
            .orElseThrow(() -> new ValidationException("Não há concurso-alvo para avaliar a prova (D-77).", "SEM_CONCURSO_ALVO", "prova"));
        var bytes = ler(prova, "prova");
        exigirPdf(bytes, "PROVA_NAO_E_PDF", "prova");
        var gabaritoEntrada = gabaritoDoEnvio(gabarito, gabaritoTexto);

        var hash = sha256(bytes);
        var existente = provaRepository.findByConcursoIdAndHashProva(alvo.getId(), hash);
        if (existente.isPresent()) {
            return new Envio(resumo(existente.get()), false);
        }
        var nome = prova.getOriginalFilename() == null || prova.getOriginalFilename().isBlank() ? "prova.pdf" : prova.getOriginalFilename();
        var criada = provaService.registrar(alvo.getId(), cortar(nome, 250), hash, tipoLimpo(tipo),
            gabaritoEntrada == null ? null : gabaritoEntrada.nome());
        arquivos.salvarEntrada(criada.getId(), "prova.pdf", bytes);
        if (gabaritoEntrada != null) arquivos.salvarEntrada(criada.getId(), gabaritoEntrada.nome(), gabaritoEntrada.bytes());
        return new Envio(resumo(criada), true);
    }

    /** O gabarito de uma prova que o esperava; ela volta a LENDO e o worker lê só o gabarito. */
    @Transactional
    public Resumo anexarGabarito(Long id, MultipartFile gabarito, String gabaritoTexto) {
        var prova = buscar(id);
        exigir(prova, StatusProva.AGUARDANDO_GABARITO);
        var entrada = gabaritoDoEnvio(gabarito, gabaritoTexto);
        if (entrada == null) {
            throw new ValidationException("Envie o gabarito (PDF ou texto).", "GABARITO_OBRIGATORIO", "gabarito");
        }
        arquivos.salvarEntrada(prova.getId(), entrada.nome(), entrada.bytes());
        prova.setGabaritoArquivo(entrada.nome());
        prova.setStatus(StatusProva.LENDO);
        return resumo(prova);
    }

    private record GabaritoEntrada(String nome, byte[] bytes) {}

    private GabaritoEntrada gabaritoDoEnvio(MultipartFile arquivo, String texto) {
        var temArquivo = arquivo != null && !arquivo.isEmpty();
        var temTexto = texto != null && !texto.isBlank();
        if (temArquivo && temTexto) {
            throw new ValidationException("Envie o gabarito como arquivo ou como texto, não os dois.", "GABARITO_DUPLICADO", "gabarito");
        }
        if (temArquivo) {
            var bytes = ler(arquivo, "gabarito");
            exigirPdf(bytes, "GABARITO_NAO_E_PDF", "gabarito");
            return new GabaritoEntrada("gabarito.pdf", bytes);
        }
        return temTexto ? new GabaritoEntrada("gabarito.txt", texto.getBytes(StandardCharsets.UTF_8)) : null;
    }

    // ------------------------------------------------------------ leitura (J-6 passo 3)

    /** As provas do concurso-alvo, da mais recente para a mais antiga. */
    @Transactional(readOnly = true)
    public List<Resumo> listar() {
        return concursoRepository.findByAtivoTrue()
            .map(alvo -> provaRepository.findByConcursoIdOrderByIdDesc(alvo.getId()).stream().map(this::resumo).toList())
            .orElse(List.of());
    }

    /** O ponto de conferência: o que foi lido, antes de gastar IA no julgamento. */
    @Transactional(readOnly = true)
    public Detalhe detalhe(Long id) {
        var prova = buscar(id);
        var questoes = questaoRepository.findByProvaIdOrderByNumeroAsc(id);
        var naoAvaliaveis = new EnumMap<MotivoNaoAvaliavel, Integer>(MotivoNaoAvaliavel.class);
        var foraDoEdital = 0;
        var avaliaveis = new ArrayList<Questao>();
        var duvidosas = new ArrayList<Duvidosa>();
        for (var q : questoes) {
            if (q.getMotivoNaoAvaliavel() != null) {
                naoAvaliaveis.merge(q.getMotivoNaoAvaliavel(), 1, Integer::sum);
            } else if (q.getAssunto() == null) {
                foraDoEdital++;
            } else {
                avaliaveis.add(q);
            }
            if (q.getAtribuicao() == AtribuicaoQuestao.REVISAR
                || (q.getAtribuicao() == AtribuicaoQuestao.PENDENTE && q.getMotivoNaoAvaliavel() == null)) {
                duvidosas.add(new Duvidosa(q.getNumero(), trecho(q.getEnunciado()), q.getAtribuicao(),
                    q.getAssunto() == null ? null : q.getAssunto().getId(),
                    q.getAssunto() == null ? null : q.getAssunto().getNome()));
            }
        }
        return new Detalhe(resumo(prova, questoes, vereditoRepository.vigentesDaProva(id).size()), prova.getFormato(),
            prova.getCargoProva(), prova.getGabaritoRecorte(), prova.getQuestoesEsperadas(), avaliaveis.size(),
            naoAvaliaveis, foraDoEdital, amostra(prova, avaliaveis), duvidosas, prova.getEtapaFalha(), prova.getErro());
    }

    /**
     * Três questões espalhadas (posições ¼, ½ e ¾ das avaliáveis), com a alternativa do gabarito:
     * o olho do usuário pega uma troca grosseira de gabarito. Espalhada, não sorteada — a mesma a
     * cada leitura, para a tela não piscar.
     */
    private List<Amostra> amostra(Prova prova, List<Questao> avaliaveis) {
        var n = avaliaveis.size();
        var posicoes = new java.util.TreeSet<Integer>();
        if (n <= 3) {
            for (var i = 0; i < n; i++) posicoes.add(i);
        } else {
            posicoes.add(n / 4);
            posicoes.add(n / 2);
            posicoes.add(3 * n / 4);
        }
        var certoErrado = prova.getFormato() == FormatoBanca.CERTO_ERRADO;
        var amostra = new ArrayList<Amostra>();
        for (var i : posicoes) {
            var q = avaliaveis.get(i);
            var alternativas = q.getAlternativas().stream().map(a -> new AlternativaLida(a.getLetra(), a.getTexto())).toList();
            var texto = certoErrado ? ("C".equals(q.getRespostaGabarito()) ? "Certo" : "Errado")
                : alternativas.stream().filter(a -> a.letra().equals(q.getRespostaGabarito())).map(AlternativaLida::texto).findFirst().orElse(null);
            amostra.add(new Amostra(q.getNumero(), q.getEnunciado(), alternativas, q.getRespostaGabarito(), texto,
                q.isLidaPorOcr(), q.getPaginaOrigem()));
        }
        return amostra;
    }

    // ------------------------------------------------------------ correção e confirmação (J-6 passos 3-5)

    @Transactional
    public Resumo corrigir(Long id, CorrecaoRequest pedido) {
        var prova = buscar(id);
        if (pedido.nome() != null) {
            if (pedido.nome().isBlank()) throw new ValidationException("O nome não pode ficar vazio.", "NOME_OBRIGATORIO", "nome");
            prova.setNome(cortar(pedido.nome().strip(), 250));
        }
        if (pedido.banca() != null) prova.setBanca(cortar(pedido.banca().strip(), 150));
        if (pedido.ano() != null) {
            if (pedido.ano() < 1900 || pedido.ano() > 2100) throw new ValidationException("Ano inválido.", "ANO_INVALIDO", "ano");
            prova.setAno(pedido.ano());
        }
        if (pedido.tipo() != null) {
            // O tipo é o do gabarito: depois de o gabarito valer, trocá-lo desfaria o recorte (D-83).
            if (prova.getStatus() != StatusProva.AGUARDANDO_GABARITO && prova.getStatus() != StatusProva.CONFERIR) {
                throw new ConflictException("O tipo só muda antes de o gabarito valer; a prova está em " + prova.getStatus() + ".", "ESTADO_INVALIDO");
            }
            prova.setTipo(tipoLimpo(pedido.tipo()));
        }
        return resumo(prova);
    }

    /**
     * Corrige a atribuição de uma questão (J-6 passo 4): assunto da cobertura do alvo, ou
     * {@code null} = fora do edital (D-74). Só na conferência: depois do julgamento a consulta de
     * D-79 não enxerga troca de assunto.
     */
    @Transactional
    public void corrigirAssunto(Long id, Integer numero, Long assuntoId) {
        var prova = buscar(id);
        exigir(prova, StatusProva.CONFERIR);
        var questao = questaoRepository.findByProvaIdAndNumero(id, numero)
            .orElseThrow(() -> new NotFoundException("Questão não encontrada nesta prova.", "QUESTAO_INEXISTENTE"));
        if (assuntoId == null) {
            questao.setAssunto(null);
            questao.setAtribuicao(AtribuicaoQuestao.FORA_DO_EDITAL);
            return;
        }
        var assunto = coberturaAtiva(prova).stream()
            .map(CoberturaEdital::getAssunto)
            .filter(a -> a.getId().equals(assuntoId))
            .findFirst()
            .orElseThrow(() -> new ValidationException("O assunto não é da cobertura do concurso-alvo (D-74).",
                "ASSUNTO_FORA_DA_COBERTURA", "assuntoId"));
        questao.setAssunto(assunto);
        questao.setAtribuicao(AtribuicaoQuestao.CONFIRMADA);
    }

    /** CONFERIR → JULGANDO: a partir daqui o worker gasta IA (J-6 passo 5). */
    @Transactional
    public Resumo confirmar(Long id) {
        var prova = buscar(id);
        exigir(prova, StatusProva.CONFERIR);
        var julgaveis = questaoRepository.findByProvaIdOrderByNumeroAsc(id).stream()
            .filter(q -> q.avaliavel() && q.getAssunto() != null).count();
        if (julgaveis == 0) {
            throw new ValidationException("Nenhuma questão avaliável com assunto no edital: não há o que julgar.",
                "SEM_QUESTAO_AVALIAVEL", "prova");
        }
        prova.setStatus(StatusProva.JULGANDO);
        return resumo(prova);
    }

    /**
     * "Analisar de novo" (D-79): PRONTA → JULGANDO se há questão a julgar — assunto cujo material
     * mudou, ou gabarito que mudou. Se nada mudou, fica PRONTA e devolve {@code aJulgar = 0}.
     */
    @Transactional
    public Reanalise analisarDeNovo(Long id) {
        var prova = buscar(id);
        exigir(prova, StatusProva.PRONTA);
        var aJulgar = questaoRepository.idsAJulgar(id).size();
        if (aJulgar > 0) prova.setStatus(StatusProva.JULGANDO);
        return new Reanalise(prova.getStatus(), aJulgar);
    }

    /** FALHOU → a etapa em que falhou, sem o erro. */
    @Transactional
    public Resumo tentarDeNovo(Long id) {
        var prova = buscar(id);
        exigir(prova, StatusProva.FALHOU);
        prova.setStatus(prova.getEtapaFalha() == StatusProva.JULGANDO ? StatusProva.JULGANDO : StatusProva.LENDO);
        prova.setEtapaFalha(null);
        prova.setErro(null);
        return resumo(prova);
    }

    // ------------------------------------------------------------ relatório (§6)

    private static final class Contador {
        int avaliaveis, respondiveis, parciais, naoRespondiveis, semVeredito;

        void somar(VereditoCobertura v) {
            avaliaveis++;
            if (v == null) semVeredito++;
            else switch (v) {
                case RESPONDIVEL -> respondiveis++;
                case PARCIAL -> parciais++;
                case NAO_RESPONDIVEL -> naoRespondiveis++;
            }
        }

        Cobertura cobertura() {
            return new Cobertura(avaliaveis, respondiveis, parciais, naoRespondiveis, semVeredito);
        }
    }

    private static final class AgregadoAssunto {
        int questoes, parciais, naoRespondiveis;
        final Set<String> faltas = new LinkedHashSet<>();
    }

    /**
     * Tudo calculado na hora (D-16) de questões, vereditos vigentes e cobertura. Sempre os dois
     * números (D-71); o que sai do denominador vem contado à parte (D-70); nunca o enunciado (D-72).
     * A nota real e a discordância com a cobertura chegam com a resolução (item 24.4).
     */
    @Transactional(readOnly = true)
    public Relatorio relatorio(Long id) {
        var prova = buscar(id);
        var questoes = questaoRepository.findByProvaIdOrderByNumeroAsc(id);
        if (questoes.isEmpty()) {
            throw new ConflictException("A prova ainda não foi lida; não há o que relatar.", "ESTADO_INVALIDO");
        }
        var vigentes = new HashMap<Long, VereditoQuestao>();
        vereditoRepository.vigentesDaProva(id).forEach(v -> vigentes.put(v.getQuestao().getId(), v));
        var cobertura = coberturaAtiva(prova);

        var total = new Contador();
        var porDisciplina = new LinkedHashMap<String, Contador>();
        var porAssunto = new HashMap<Long, AgregadoAssunto>();
        var comQuestao = new HashSet<Long>();
        var foraDoEdital = 0;
        var anuladas = 0;
        var semGabarito = 0;
        var semTexto = 0;
        var ocrDuvidoso = 0;
        var linhas = new ArrayList<QuestaoRelatorio>();

        for (var q : questoes) {
            var v = vigentes.get(q.getId());
            var assunto = q.getAssunto();
            if (assunto != null) comQuestao.add(assunto.getId());
            if (q.getMotivoNaoAvaliavel() != null) {
                switch (q.getMotivoNaoAvaliavel()) {
                    case ANULADA -> anuladas++;
                    case SEM_GABARITO -> semGabarito++;
                    case SEM_TEXTO -> semTexto++;
                    case OCR_DUVIDOSO -> ocrDuvidoso++;
                }
            } else if (assunto == null) {
                foraDoEdital++;
            } else {
                var veredito = v == null ? null : v.getVeredito();
                total.somar(veredito);
                porDisciplina.computeIfAbsent(assunto.getDisciplina().getNome(), d -> new Contador()).somar(veredito);
                var agregado = porAssunto.computeIfAbsent(assunto.getId(), a -> new AgregadoAssunto());
                agregado.questoes++;
                if (veredito == VereditoCobertura.PARCIAL) agregado.parciais++;
                if (veredito == VereditoCobertura.NAO_RESPONDIVEL) agregado.naoRespondiveis++;
                if (v != null && veredito != VereditoCobertura.RESPONDIVEL && v.getFalta() != null && !v.getFalta().isBlank()
                    && agregado.faltas.size() < MAX_FALTAS) {
                    agregado.faltas.add(v.getFalta());
                }
            }
            linhas.add(new QuestaoRelatorio(q.getNumero(), assunto == null ? null : assunto.getId(),
                assunto == null ? null : assunto.getNome(), q.getAtribuicao(), q.getMotivoNaoAvaliavel(),
                v == null ? null : v.getVeredito(),
                v == null || v.getSegmento() == null ? null : v.getSegmento().getId(),
                v == null ? null : v.getPaginaSegmento(), v == null ? null : v.getFalta(),
                q.isLidaPorOcr(), q.getPaginaOrigem()));
        }

        // Na ordem do edital (disciplina, depois ordem): a mesma de Concursos.
        var ordemDisciplina = new LinkedHashMap<String, Integer>();
        var ordemAssunto = new HashMap<Long, Integer>();
        var semQuestao = new ArrayList<AssuntoSemQuestao>();
        for (var c : cobertura) {
            ordemDisciplina.putIfAbsent(c.getDisciplina().getNome(), ordemDisciplina.size());
            ordemAssunto.put(c.getAssunto().getId(), ordemAssunto.size());
            if (!comQuestao.contains(c.getAssunto().getId())) {
                semQuestao.add(new AssuntoSemQuestao(c.getAssunto().getId(), c.getAssunto().getNome(), c.getDisciplina().getNome()));
            }
        }
        var disciplinas = porDisciplina.entrySet().stream()
            .sorted(java.util.Map.Entry.comparingByKey(java.util.Comparator.comparing(d -> ordemDisciplina.getOrDefault(d, Integer.MAX_VALUE))))
            .map(e -> new PorDisciplina(e.getKey(), e.getValue().cobertura()))
            .toList();

        var furados = new ArrayList<AssuntoFurado>();
        for (var c : cobertura) {
            var a = porAssunto.get(c.getAssunto().getId());
            if (a != null && a.parciais + a.naoRespondiveis > 0) {
                furados.add(new AssuntoFurado(c.getAssunto().getId(), c.getAssunto().getNome(), c.getDisciplina().getNome(),
                    a.questoes, a.parciais, a.naoRespondiveis, List.copyOf(a.faltas)));
            }
        }
        // Do mais furado ao menos; empate fica na ordem do edital (a lista já vem nela).
        furados.sort(java.util.Comparator.comparingInt((AssuntoFurado f) -> -(f.parciais() + f.naoRespondiveis())));

        return new Relatorio(prova.getId(), prova.getNome(), prova.getBanca(), prova.getAno(), prova.getTipo(),
            prova.getStatus(), prova.getGabaritoSituacao() == br.com.estudos.shared.enums.SituacaoGabarito.PRELIMINAR,
            prova.getGabaritoRecorte(), total.cobertura(),
            new Declaradas(foraDoEdital, anuladas, semGabarito, semTexto, ocrDuvidoso),
            disciplinas, furados, semQuestao, linhas);
    }

    // ------------------------------------------------------------ internos

    private Prova buscar(Long id) {
        return provaRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Prova não encontrada.", "PROVA_INEXISTENTE"));
    }

    private void exigir(Prova prova, StatusProva esperado) {
        if (prova.getStatus() != esperado) {
            throw new ConflictException("A prova está em " + prova.getStatus() + "; esta ação exige " + esperado + ".", "ESTADO_INVALIDO");
        }
    }

    /** Assuntos ativos da cobertura do concurso da prova, na ordem do edital (D-74, D-77). */
    private List<CoberturaEdital> coberturaAtiva(Prova prova) {
        return coberturaRepository.listarDoConcurso(prova.getConcurso().getId()).stream()
            .filter(c -> c.getAssunto().isAtivo())
            .toList();
    }

    private Resumo resumo(Prova prova) {
        return resumo(prova, questaoRepository.findByProvaIdOrderByNumeroAsc(prova.getId()),
            vereditoRepository.vigentesDaProva(prova.getId()).size());
    }

    private Resumo resumo(Prova p, List<Questao> questoes, int julgadas) {
        var avaliaveis = (int) questoes.stream().filter(q -> q.avaliavel() && q.getAssunto() != null).count();
        return new Resumo(p.getId(), p.getNome(), p.getBanca(), p.getAno(), p.getTipo(), p.getStatus(),
            p.getGabaritoSituacao(), p.getGabaritoArquivo() != null || p.getGabaritoSituacao() != null,
            p.getCriadoEm(), questoes.size(), avaliaveis, julgadas);
    }

    private byte[] ler(MultipartFile arquivo, String campo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ValidationException("O arquivo '" + campo + "' veio vazio.", "ARQUIVO_VAZIO", campo);
        }
        try {
            return arquivo.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Não foi possível ler o arquivo '" + campo + "'.", "ARQUIVO_ILEGIVEL", campo);
        }
    }

    private void exigirPdf(byte[] bytes, String codigo, String campo) {
        if (bytes.length < PDF.length || !java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, PDF.length), PDF)) {
            throw new ValidationException("O arquivo '" + campo + "' não é um PDF.", codigo, campo);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            var resumo = MessageDigest.getInstance("SHA-256").digest(bytes);
            var hex = new StringBuilder();
            for (var b : resumo) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String tipoLimpo(String tipo) {
        return tipo == null || tipo.isBlank() ? null : cortar(tipo.strip(), 10);
    }

    private static String cortar(String texto, int max) {
        return texto.length() > max ? texto.substring(0, max) : texto;
    }

    private static String trecho(String enunciado) {
        if (enunciado == null) return null;
        var limpo = enunciado.strip().replaceAll("\\s+", " ");
        return limpo.length() > TRECHO ? limpo.substring(0, TRECHO) + "…" : limpo;
    }
}
