package br.com.estudos.edital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.concurso.ConcursoRepository;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Análise de edital por IA (01_DOMINIO §3.9, ADR-043, docs/SPRINT-18-CONCURSOS.md §7)
 * contra um servidor HTTP falso no lugar do OpenRouter, com a análise
 * rodando na própria thread da requisição (estudos.ia.sincrono).
 */
class AnaliseEditalTest extends IntegracaoTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpServer OPENROUTER;
    private static final List<String> PEDIDOS = new CopyOnWriteArrayList<>();
    private static volatile String respostaEtapa1;
    private static volatile String respostaEtapa2;
    private static volatile int statusHttp = 200;

    static {
        try {
            OPENROUTER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        OPENROUTER.createContext("/api/v1/chat/completions", troca -> {
            var corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            PEDIDOS.add(corpo);
            var conteudo = corpo.contains("ETAPA 1") ? respostaEtapa1 : respostaEtapa2;
            var resposta = statusHttp == 200
                ? JSON.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", conteudo)))))
                : "{\"error\":{\"message\":\"No auth credentials found\"}}";
            var bytes = resposta.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(statusHttp, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        OPENROUTER.start();
    }

    @DynamicPropertySource
    static void ia(DynamicPropertyRegistry registro) {
        registro.add("estudos.ia.chave", () -> "chave-de-teste");
        registro.add("estudos.ia.url-base", () -> "http://127.0.0.1:" + OPENROUTER.getAddress().getPort() + "/api/v1");
        registro.add("estudos.ia.sincrono", () -> "true");
    }

    @Autowired private DisciplinaRepository disciplinaRepository;
    @Autowired private AssuntoRepository assuntoRepository;
    @Autowired private ConcursoRepository concursoRepository;

    private String sufixo;

    @BeforeEach
    void preparar() {
        sufixo = " " + System.nanoTime();
        statusHttp = 200;
        PEDIDOS.clear();
        respostaEtapa1 = """
            {"concurso": "TRT Teste 2026", "banca": "FCC", "dataProva": "2026-12-20",
             "cargos": ["Analista Judiciario - Tecnologia da Informacao", "Tecnico Judiciario"]}""";
    }

    @Test
    void fluxoCompleto_cargoPropostaRevisaoConfirmacao() throws Exception {
        long disciplinaId = criarDisciplina("Redes IA" + sufixo);
        long assuntoId = criarAssunto(disciplinaId, "TCP/IP IA" + sufixo);
        respostaEtapa2 = """
            {"disciplinas": [
              {"nome": "Redes IA%1$s", "questoes": 20, "disciplinaExistenteId": %2$d,
               "assuntos": [{"nome": "Modelo TCP/IP", "assuntoExistenteId": %3$d},
                            {"nome": "Roteamento IA%1$s", "assuntoExistenteId": null}]},
              {"nome": "Portugues IA%1$s", "questoes": 10,
               "assuntos": [{"nome": "Crase IA%1$s"}, {"nome": "crase ia%1$s"}]},
              {"nome": "Etica IA%1$s", "questoes": null,
               "assuntos": [{"nome": "Lei 8112 IA%1$s", "assuntoExistenteId": 999999999}]}
            ]}""".formatted(sufixo, disciplinaId, assuntoId);

        var analise = enviarEdital();
        assertEquals("ESCOLHER_CARGO", JsonPath.read(analise, "$.status"));
        assertEquals("TRT Teste 2026", JsonPath.read(analise, "$.concursoNome"));
        assertEquals("2026-12-20", JsonPath.read(analise, "$.dataProva"));
        Integer id = JsonPath.read(analise, "$.id");

        var pronta = mockMvc.perform(post("/api/editais/analises/{id}/cargo", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cargo\": \"Analista Judiciario - Tecnologia da Informacao\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PRONTA"))
            // D-61: 20 questões → ALTO, 10 → MEDIO (dois com contagem), sem contagem → MEDIO não determinado.
            .andExpect(jsonPath("$.proposta[0].peso").value("ALTO"))
            .andExpect(jsonPath("$.proposta[1].peso").value("MEDIO"))
            .andExpect(jsonPath("$.proposta[2].peso").value("MEDIO"))
            .andExpect(jsonPath("$.proposta[2].pesoDeterminado").value(false))
            .andExpect(jsonPath("$.proposta[0].assuntos[0].assuntoExistenteId").value(assuntoId))
            // Repetido sem acento/caixa sai; D-63: id inexistente vira novo.
            .andExpect(jsonPath("$.proposta[1].assuntos.length()").value(1))
            .andExpect(jsonPath("$.proposta[2].assuntos[0].assuntoExistenteId").doesNotExist())
            .andExpect(jsonPath("$.resumo.novos").value(3))
            .andExpect(jsonPath("$.resumo.emComum").value(1))
            .andReturn().getResponse().getContentAsString();
        assertTrue(PEDIDOS.get(1).contains("TCP/IP IA" + sufixo), "a base atual vai no pedido da etapa 2");

        // D-62: nada gravado antes de confirmar.
        assertTrue(disciplinaRepository.findByNomeIgnoreCase("Portugues IA" + sufixo).isEmpty());

        var revisada = """
            {"nome": "TRT Teste 2026", "cargo": "Analista", "banca": "FCC", "dataProva": "2026-12-20",
             "disciplinas": [
               {"nome": "Redes IA%1$s", "disciplinaExistenteId": %2$d, "peso": "ALTO",
                "assuntos": [{"nome": "Modelo TCP/IP", "assuntoExistenteId": %3$d},
                             {"nome": "Roteamento IA%1$s", "assuntoExistenteId": null}]},
               {"nome": "Portugues IA%1$s", "disciplinaExistenteId": null, "peso": "MEDIO",
                "assuntos": [{"nome": "Crase IA%1$s", "assuntoExistenteId": null}]},
               {"nome": "Etica IA%1$s", "disciplinaExistenteId": null, "peso": "BAIXO",
                "assuntos": [{"nome": "Lei 8112 IA%1$s", "assuntoExistenteId": 999999999}]}
             ]}""".formatted(sufixo, disciplinaId, assuntoId);
        mockMvc.perform(post("/api/editais/analises/{id}/confirmar", id)
                .contentType(MediaType.APPLICATION_JSON).content(revisada))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ativo").value(true))
            .andExpect(jsonPath("$.origem").value("ANALISE_IA"))
            .andExpect(jsonPath("$.totalAssuntos").value(4));

        var existente = assuntoRepository.findById(assuntoId).orElseThrow();
        assertEquals(TipoPeso.ALTO, existente.getPeso(), "assunto em comum recebe o peso do edital (D-60)");
        assertEquals(1, existente.getOrdem());
        assertEquals("TCP/IP IA" + sufixo, existente.getNome(), "o nome do assunto existente não muda");
        var etica = disciplinaRepository.findByNomeIgnoreCase("Etica IA" + sufixo).orElseThrow();
        assertEquals(TipoPeso.BAIXO, etica.getPeso(), "o peso trocado na revisão é o que vale");
        assertEquals(1, assuntoRepository.findByDisciplinaIdAndAtivoTrue(etica.getId()).size());

        mockMvc.perform(get("/api/editais/analises/{id}", id))
            .andExpect(jsonPath("$.status").value("CONFIRMADA"))
            .andExpect(jsonPath("$.concursoId").isNumber());
        mockMvc.perform(get("/api/concursos/ativo"))
            .andExpect(jsonPath("$.nome").value("TRT Teste 2026"))
            .andExpect(jsonPath("$.cargo").value("Analista"));
    }

    @Test
    void respostaForaDoFormato_falhaETentarDeNovoRepeteAEtapa() throws Exception {
        respostaEtapa1 = "Desculpe, não consegui ler.";
        var analise = enviarEdital();
        assertEquals("FALHOU", JsonPath.read(analise, "$.status"));
        assertEquals("LENDO_EDITAL", JsonPath.read(analise, "$.etapaFalha"));
        assertFalse(((String) JsonPath.read(analise, "$.erro")).isBlank());
        Integer id = JsonPath.read(analise, "$.id");

        respostaEtapa1 = "```json\n{\"concurso\": \"X\", \"cargos\": [\"Cargo unico\"]}\n```";
        mockMvc.perform(post("/api/editais/analises/{id}/tentar-de-novo", id))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("ESCOLHER_CARGO"))
            .andExpect(jsonPath("$.cargos[0]").value("Cargo unico"))
            .andExpect(jsonPath("$.erro").doesNotExist());
    }

    @Test
    void chaveRecusada_falhaComOMotivo() throws Exception {
        statusHttp = 401;
        var analise = enviarEdital();
        assertEquals("FALHOU", JsonPath.read(analise, "$.status"));
        assertTrue(((String) JsonPath.read(analise, "$.erro")).contains("Ajustes → IA"), "a mensagem diz onde corrigir a chave (ADR-044)");
    }

    @Test
    void confirmarForaDaEtapa_409() throws Exception {
        Integer id = JsonPath.read(enviarEdital(), "$.id");
        mockMvc.perform(post("/api/editais/analises/{id}/confirmar", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"nome\": \"X\", \"disciplinas\": []}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ETAPA_INVALIDA"));
        mockMvc.perform(post("/api/editais/analises/{id}/cargo", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cargo\": \"inventado\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("CARGO_INVALIDO"));
    }

    @Test
    void pdfSemTexto_422() throws Exception {
        mockMvc.perform(multipart("/api/editais/analises")
                .file(new MockMultipartFile("arquivo", "escaneado.pdf", "application/pdf", pdf("Pagina 1"))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("PDF_SEM_TEXTO"));
        mockMvc.perform(multipart("/api/editais/analises")
                .file(new MockMultipartFile("arquivo", "nao.pdf", "application/pdf", "texto".getBytes())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("ARQUIVO_NAO_PDF"));
    }

    /**
     * D-64 (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §2): a confirmação dá
     * identificador ao concurso e chave a cada assunto novo; o material entregue
     * pelo planejamento com esse identificador reencontra o mesmo concurso —
     * antes criava um segundo, sem cargo, e o ativava no lugar.
     */
    @Test
    void confirmar_d64_slugEChaves_eConcursoJsonDoMesmoSlugNaoDuplica() throws Exception {
        var nome = "Concurso D64" + sufixo;
        long concursoId = confirmarEditalSimples(nome);

        var entrega = mockMvc.perform(get("/api/concursos/{id}/planejamento", concursoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assuntos.length()").value(2))
            .andExpect(jsonPath("$.assuntos[0].segmentos").value(0))
            .andExpect(jsonPath("$.assuntos[0].lacuna").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(entrega, "$.slug");
        assertEquals(("concurso-d64" + sufixo).replace(' ', '-'), slug);
        List<String> chaves = JsonPath.read(entrega, "$.assuntos[*].chaveExterna");
        assertTrue(chaves.stream().allMatch(c -> c != null && c.length() == 36), "todo assunto confirmado tem chave");
        List<String> nomes = JsonPath.read(entrega, "$.assuntos[*].assunto");

        long antes = concursoRepository.count();
        var csv = "disciplina,assunto,peso,ordem,chaveExterna\n"
            + "Direito D64" + sufixo + "," + nomes.get(0) + ",MEDIO,1," + chaves.get(0) + "\n"
            + "Direito D64" + sufixo + "," + nomes.get(1) + ",MEDIO,2," + chaves.get(1) + "\n";
        var concursoJson = "{\"slug\": \"" + slug + "\", \"nome\": \"" + nome + "\", \"dataProva\": \"2026-12-20\"}";
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar")
                .file(new MockMultipartFile("arquivo", "assuntos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("concurso", "concurso.json", "application/json", concursoJson.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk());

        assertEquals(antes, concursoRepository.count(), "o mesmo slug não cria segundo concurso");
        mockMvc.perform(get("/api/concursos/ativo"))
            .andExpect(jsonPath("$.id").value(concursoId))
            .andExpect(jsonPath("$.cargo").value("Analista"));
    }

    @Test
    void confirmar_d64_nomeRepetidoGanhaSufixoNoSlug() throws Exception {
        var nome = "Concurso Repetido" + sufixo;
        long primeiro = confirmarEditalSimples(nome);
        long segundo = confirmarEditalSimples(nome);

        var slugPrimeiro = concursoRepository.findById(primeiro).orElseThrow().getSlug();
        var slugSegundo = concursoRepository.findById(segundo).orElseThrow().getSlug();
        assertEquals(slugPrimeiro + "-2", slugSegundo);
    }

    @Test
    void iaDisponivel() throws Exception {
        mockMvc.perform(get("/api/editais/ia"))
            .andExpect(jsonPath("$.disponivel").value(true));
    }

    // ------------------------------------------------------------ apoio

    /** Edital com uma disciplina nova e dois assuntos novos, confirmado sem mudança. Devolve o id do concurso. */
    private long confirmarEditalSimples(String nomeConcurso) throws Exception {
        var disciplina = "Direito D64" + sufixo;
        respostaEtapa2 = """
            {"disciplinas": [{"nome": "%1$s", "questoes": 10,
              "assuntos": [{"nome": "Atos administrativos%2$s"}, {"nome": "Licitacoes%2$s"}]}]}"""
            .formatted(disciplina, sufixo);
        Integer id = JsonPath.read(enviarEdital(), "$.id");
        mockMvc.perform(post("/api/editais/analises/{id}/cargo", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cargo\": \"Tecnico Judiciario\"}"))
            .andExpect(status().isAccepted());
        var confirmada = """
            {"nome": "%1$s", "cargo": "Analista", "banca": "FCC", "dataProva": "2026-12-20",
             "disciplinas": [{"nome": "%2$s", "disciplinaExistenteId": null, "peso": "MEDIO",
               "assuntos": [{"nome": "Atos administrativos%3$s", "assuntoExistenteId": null},
                            {"nome": "Licitacoes%3$s", "assuntoExistenteId": null}]}]}"""
            .formatted(nomeConcurso, disciplina, sufixo);
        var resposta = mockMvc.perform(post("/api/editais/analises/{id}/confirmar", id)
                .contentType(MediaType.APPLICATION_JSON).content(confirmada))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private String enviarEdital() throws Exception {
        var texto = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            texto.append("Item ").append(i).append(" do conteudo programatico: redes, portugues e etica no servico publico.\n");
        }
        return mockMvc.perform(multipart("/api/editais/analises")
                .file(new MockMultipartFile("arquivo", "edital.pdf", "application/pdf", pdf(texto.toString()))))
            .andExpect(status().isAccepted())
            .andReturn().getResponse().getContentAsString();
    }

    static byte[] pdf(String texto) throws IOException {
        try (var documento = new PDDocument(); var saida = new ByteArrayOutputStream()) {
            var pagina = new PDPage();
            documento.addPage(pagina);
            try (var conteudo = new PDPageContentStream(documento, pagina)) {
                conteudo.beginText();
                conteudo.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                conteudo.setLeading(11);
                conteudo.newLineAtOffset(30, 760);
                for (var linha : texto.split("\n")) {
                    conteudo.showText(linha);
                    conteudo.newLine();
                }
                conteudo.endText();
            }
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private long criarDisciplina(String nome) throws Exception {
        var resposta = mockMvc.perform(post("/api/disciplinas").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \"" + nome + "\", \"peso\": \"MEDIO\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }

    private long criarAssunto(long disciplinaId, String nome) throws Exception {
        var resposta = mockMvc.perform(post("/api/assuntos").contentType(MediaType.APPLICATION_JSON)
                .content("{\"disciplinaId\": " + disciplinaId + ", \"nome\": \"" + nome
                    + "\", \"peso\": \"BAIXO\", \"dificuldadePercebida\": 3, \"ordem\": 1}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(resposta, "$.id")).longValue();
    }
}
