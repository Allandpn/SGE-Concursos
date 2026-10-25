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
import java.nio.file.Files;
import java.nio.file.Path;
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

import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.StatusAnaliseEdital;

/**
 * Leitura do cabeçalho do edital (01_DOMINIO §3.9, ADR-043 e ADR-047, docs/SPRINT-25-CONFERENCIA-EDITAL.md §1)
 * contra um servidor HTTP falso no lugar do OpenRouter, com a análise
 * rodando na própria thread da requisição (estudos.ia.sincrono).
 */
class AnaliseEditalTest extends IntegracaoTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpServer OPENROUTER;
    private static final List<String> PEDIDOS = new CopyOnWriteArrayList<>();
    private static volatile String respostaEtapa1;
    private static volatile int statusHttp = 200;
    private static final Path EDITAIS;

    static {
        try {
            EDITAIS = Files.createTempDirectory("editais-teste");
            OPENROUTER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        OPENROUTER.createContext("/api/v1/chat/completions", troca -> {
            var corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            PEDIDOS.add(corpo);
            var conteudo = respostaEtapa1;
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
        registro.add("estudos.editais.dir", EDITAIS::toString);
    }

    @Autowired private AnaliseEditalRepository analiseRepository;

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
    void fluxo_cabecalhoCargoEPdfGuardado_aoEscolherOCargoOPlanejamentoPassaAPegar() throws Exception {
        var analise = enviarEdital();
        assertEquals("ESCOLHER_CARGO", JsonPath.read(analise, "$.status"));
        assertEquals("TRT Teste 2026", JsonPath.read(analise, "$.concursoNome"));
        assertEquals("2026-12-20", JsonPath.read(analise, "$.dataProva"));
        Integer id = JsonPath.read(analise, "$.id");
        assertTrue(Files.isRegularFile(EDITAIS.resolve(id + "/edital.pdf")), "o PDF fica no volume até a conferência terminar");
        assertEquals(id + "/edital.pdf", analiseRepository.findById(id.longValue()).orElseThrow().getPdfArquivo());

        mockMvc.perform(post("/api/editais/analises/{id}/cargo", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cargo\": \"Analista Judiciario - Tecnologia da Informacao\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("SEPARANDO_ASSUNTOS"))
            .andExpect(jsonPath("$.cargoEscolhido").value("Analista Judiciario - Tecnologia da Informacao"));
        assertEquals(1, PEDIDOS.size(), "separar os assuntos não é mais com a IA do SGE (ADR-047)");
    }

    @Test
    void falhaDoPlanejamento_tentarDeNovoDevolveAAnaliseAoPlanejamento() throws Exception {
        Integer id = JsonPath.read(enviarEdital(), "$.id");
        var analise = analiseRepository.findById(id.longValue()).orElseThrow();
        analise.setStatus(StatusAnaliseEdital.FALHOU);
        analise.setEtapaFalha("QUEBRA");
        analise.setErro("A IA respondeu fora do formato.");
        analiseRepository.save(analise);

        mockMvc.perform(post("/api/editais/analises/{id}/tentar-de-novo", id))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("SEPARANDO_ASSUNTOS"))
            .andExpect(jsonPath("$.etapaFalha").doesNotExist())
            .andExpect(jsonPath("$.erro").doesNotExist());
        assertEquals(1, PEDIDOS.size(), "nada foi para a IA do SGE");
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
    void cargoInvalido_422_eForaDaEtapa_409() throws Exception {
        Integer id = JsonPath.read(enviarEdital(), "$.id");
        mockMvc.perform(post("/api/editais/analises/{id}/cargo", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cargo\": \"inventado\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("CARGO_INVALIDO"));
        mockMvc.perform(post("/api/editais/analises/{id}/cargo", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cargo\": \"Tecnico Judiciario\"}"))
            .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/editais/analises/{id}/cargo", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cargo\": \"Tecnico Judiciario\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ETAPA_INVALIDA"));
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

    @Test
    void iaDisponivel() throws Exception {
        mockMvc.perform(get("/api/editais/ia"))
            .andExpect(jsonPath("$.disponivel").value(true));
    }

    // ------------------------------------------------------------ apoio

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
}
