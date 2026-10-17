package br.com.estudos.segmento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Sprint 19, Fase A (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3–§6, §8): rotas
 * internas com token, proxy de IA, lacuna (D-65), estado do material, PDFs
 * servidos do diretório e Ajustes → IA sem nunca devolver a chave.
 *
 * @Transactional: a chave cadastrada em Ajustes não pode vazar para as outras
 * classes de teste (que usam a do ambiente).
 */
@Transactional
class MaterialAutomaticoTest extends IntegracaoTestBase {

    private static final String TOKEN = "token-interno-de-teste";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpServer OPENROUTER;
    private static final Path MATERIAL;

    static {
        try {
            OPENROUTER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            MATERIAL = Files.createTempDirectory("material");
            Files.createDirectories(MATERIAL.resolve("blocos/atos"));
            Files.writeString(MATERIAL.resolve("blocos/atos/segmento-01.pdf"), "%PDF-1.4 teste");
            Files.writeString(MATERIAL.getParent().resolve("fora-do-material.txt"), "segredo");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        OPENROUTER.createContext("/api/v1/chat/completions", troca -> {
            var resposta = JSON.writeValueAsString(Map.of("choices",
                List.of(Map.of("message", Map.of("content", "{\"fontes\": []}")))));
            responder(troca, resposta);
        });
        OPENROUTER.createContext("/api/v1/models", troca -> responder(troca, """
            {"data": [
              {"id": "z/modelo-b", "name": "Zeta B", "pricing": {"prompt": "0.000003", "completion": "0.000015"}},
              {"id": "a/modelo-a", "name": "Alfa A", "pricing": {"prompt": "0", "completion": "0"}}
            ]}"""));
        OPENROUTER.start();
    }

    private static void responder(com.sun.net.httpserver.HttpExchange troca, String corpo) throws IOException {
        var bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(200, bytes.length);
        troca.getResponseBody().write(bytes);
        troca.close();
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("estudos.ia.chave", () -> "chave-do-ambiente-1234");
        registro.add("estudos.ia.url-base", () -> "http://127.0.0.1:" + OPENROUTER.getAddress().getPort() + "/api/v1");
        registro.add("estudos.interno.token", () -> TOKEN);
        registro.add("estudos.material.dir", MATERIAL::toString);
    }

    @Autowired private DisciplinaService disciplinaService;
    @Autowired private AssuntoService assuntoService;
    @Autowired private MaterialService materialService;
    @Autowired private SegmentoService segmentoService;

    // ------------------------------------------------------------ rotas internas

    @Test
    void semToken_recusa() throws Exception {
        mockMvc.perform(post("/internal/ia/completar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sistema\": \"s\", \"usuario\": \"u\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
        mockMvc.perform(post("/internal/ia/completar").header("X-Internal-Token", "errado")
                .contentType(MediaType.APPLICATION_JSON).content("{\"sistema\": \"s\", \"usuario\": \"u\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCerto_completaPeloOpenRouter() throws Exception {
        mockMvc.perform(post("/internal/ia/completar").header("X-Internal-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"sistema\": \"s\", \"usuario\": \"u\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conteudo").value("{\"fontes\": []}"))
            .andExpect(jsonPath("$.modelo").isString());
    }

    @Test
    void foraDeInternal_naoPedeToken() throws Exception {
        mockMvc.perform(get("/api/ia/configuracao")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------ lacuna e estado (D-65)

    @Test
    void lacunaAparece_segmentoVence() throws Exception {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Material " + UUID.randomUUID(), TipoPeso.MEDIO));
        var chave = UUID.randomUUID().toString();
        var assunto = assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Atos", TipoPeso.MEDIO, (short) 3, 1, chave));

        mockMvc.perform(post("/internal/material/lacunas").header("X-Internal-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chaveExterna\": \"" + chave + "\", \"motivo\": \"não achado no acervo\"}"))
            .andExpect(status().isNoContent());

        assertEquals(MaterialService.Situacao.SEM_MATERIAL, materialService.estados(List.of(assunto.getId())).get(assunto.getId()).situacao());
        mockMvc.perform(get("/api/assuntos/{id}/lacuna", assunto.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.motivo").value("não achado no acervo"));

        segmentoService.criarOuAtualizar(new SegmentoRequest(
            assunto.getId(), "seg-" + UUID.randomUUID(), 1, "/material/blocos/atos/segmento-01.pdf", 1, 10, 40));

        assertEquals(MaterialService.Situacao.PRONTO, materialService.estados(List.of(assunto.getId())).get(assunto.getId()).situacao(),
            "D-65: o assunto que ganhou material está pronto, mesmo com lacuna antiga");
    }

    @Test
    void lacunaSemMotivo_ou_assuntoInexistente_recusa() throws Exception {
        mockMvc.perform(post("/internal/material/lacunas").header("X-Internal-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chaveExterna\": \"nao-existe\", \"motivo\": \"x\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/internal/material/lacunas").header("X-Internal-Token", TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"chaveExterna\": \"nao-existe\", \"motivo\": \"  \"}"))
            .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------ PDFs servidos

    @Test
    void serveDoDiretorio_naoSaiDele() throws Exception {
        mockMvc.perform(get("/material/blocos/atos/segmento-01.pdf"))
            .andExpect(status().isOk())
            .andExpect(content().string("%PDF-1.4 teste"));
        mockMvc.perform(get("/material/../fora-do-material.txt"))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/material/blocos/atos/nao-existe.pdf"))
            .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ Ajustes → IA

    @Test
    void semAjuste_valeOAmbiente_eAChaveNuncaVolta() throws Exception {
        var corpo = mockMvc.perform(get("/api/ia/configuracao"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disponivel").value(true))
            .andExpect(jsonPath("$.provedor").value("OPENROUTER")) // sem token do Claude, o OpenRouter
            .andExpect(jsonPath("$.openrouter.origem").value("AMBIENTE"))
            .andExpect(jsonPath("$.openrouter.chaveFinal").value("1234"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(!corpo.contains("chave-do-ambiente"), "a chave nunca sai na resposta");
    }

    @Test
    void cadastrarChaveEModelo_passaAValerSemReiniciar() throws Exception {
        var corpo = mockMvc.perform(put("/api/ia/configuracao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"chave\": \"sk-or-cadastrada-9876\", \"modelo\": \"a/modelo-a\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openrouter.origem").value("AJUSTES"))
            .andExpect(jsonPath("$.openrouter.chaveFinal").value("9876"))
            .andExpect(jsonPath("$.openrouter.modelo").value("a/modelo-a"))
            .andReturn().getResponse().getContentAsString();
        assertTrue(!corpo.contains("sk-or-cadastrada"), "a chave nunca sai na resposta");

        // Só o modelo: a chave cadastrada continua.
        mockMvc.perform(put("/api/ia/configuracao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"modelo\": \"z/modelo-b\"}"))
            .andExpect(jsonPath("$.openrouter.chaveFinal").value("9876"))
            .andExpect(jsonPath("$.openrouter.modelo").value("z/modelo-b"));
    }

    @Test
    void chaveCurta_recusa() throws Exception {
        mockMvc.perform(put("/api/ia/configuracao").contentType(MediaType.APPLICATION_JSON)
                .content("{\"chave\": \"abc\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("CHAVE_INVALIDA"));
    }

    @Test
    void modelos_ordenadosComPrecoPorMilhao() throws Exception {
        mockMvc.perform(get("/api/ia/modelos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("a/modelo-a"))
            .andExpect(jsonPath("$[1].nome").value("Zeta B"))
            .andExpect(jsonPath("$[1].precoEntradaPorMilhao").value(3.0))
            .andExpect(jsonPath("$[1].precoSaidaPorMilhao").value(15.0));
    }
}
