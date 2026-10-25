package br.com.estudos.arvore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import br.com.estudos.shared.ConferenciaTestBase;

/**
 * A árvore do QConcursos (01_DOMINIO §3.12, D-90; docs/SPRINT-25-CONFERENCIA-EDITAL.md §2.2, §4, §5): id de disciplina
 * negado (colide com o de assunto), carga idempotente que não troca a versão vigente, diferença entre versões, busca.
 */
public class ArvoreTest extends ConferenciaTestBase {

    /** A disciplina 273 e o assunto 273 existem juntos, como no QConcursos real. */
    public static final String V1 = """
        {"fonte": "qconcursos", "data": "2026-10-19", "quantidade_nos": 7, "disciplinas": [
          {"id": 10, "nome": "Banco de Dados", "ordem": 1, "filhos": [
            {"id": 273, "nome": "Arquitetura de Banco de Dados", "ordem": 1, "filhos": []},
            {"id": 12, "nome": "SQL", "ordem": 2, "filhos": [
              {"id": 121, "nome": "Window Function", "ordem": 1, "filhos": []},
              {"id": 122, "nome": "Álgebra Relacional", "ordem": 2, "filhos": []}]}]},
          {"id": 273, "nome": "Antropologia", "ordem": 2, "filhos": [
            {"id": 5, "nome": "Cultura", "ordem": 1, "filhos": []}]}]}""";

    /** Renomeia 121, remove 5, move 122 para baixo de 273 (assunto) e cria 6. */
    public static final String V2 = """
        {"fonte": "qconcursos", "data": "2026-11-20", "quantidade_nos": 7, "disciplinas": [
          {"id": 10, "nome": "Banco de Dados", "ordem": 1, "filhos": [
            {"id": 273, "nome": "Arquitetura de Banco de Dados", "ordem": 1, "filhos": [
              {"id": 122, "nome": "Álgebra Relacional", "ordem": 1, "filhos": []}]},
            {"id": 12, "nome": "SQL", "ordem": 2, "filhos": [
              {"id": 121, "nome": "Funções de Janela", "ordem": 1, "filhos": []},
              {"id": 6, "nome": "CTE", "ordem": 2, "filhos": []}]}]},
          {"id": 273, "nome": "Antropologia", "ordem": 2, "filhos": []}]}""";

    private void carregar(String json) throws Exception {
        mockMvc.perform(internoPost("/internal/arvores", json)).andExpect(status().isOk());
    }

    @Test
    void primeiraVersaoFicaVigente_eIdDeDisciplinaEhNegado_semColidirComOAssunto() throws Exception {
        mockMvc.perform(internoPost("/internal/arvores", V1))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rotulo").value("qconcursos-2026-10-19"))
            .andExpect(jsonPath("$.vigente").value(true))
            .andExpect(jsonPath("$.quantidadeNos").value(7));

        mockMvc.perform(get("/api/arvore/nos/{id}", 273))
            .andExpect(jsonPath("$.nome").value("Arquitetura de Banco de Dados"))
            .andExpect(jsonPath("$.caminho[0]").value("Banco de Dados"))
            .andExpect(jsonPath("$.raizId").value(-10));
        mockMvc.perform(get("/api/arvore/nos/{id}", -273))
            .andExpect(jsonPath("$.nome").value("Antropologia"))
            .andExpect(jsonPath("$.filhos").value(1));
    }

    @Test
    void cargaEhIdempotentePeloRotulo() throws Exception {
        carregar(V1);
        carregar(V1);
        mockMvc.perform(get("/api/arvore/versoes")).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void segundaVersaoNaoTrocaAVigente_eADiferencaContraAVigenteEhCalculada() throws Exception {
        carregar(V1);
        var v2 = mockMvc.perform(internoPost("/internal/arvores", V2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.vigente").value(false))
            .andExpect(jsonPath("$.diferencas.NOVO").value(1))
            .andExpect(jsonPath("$.diferencas.REMOVIDO").value(1))
            .andExpect(jsonPath("$.diferencas.RENOMEADO").value(1))
            .andExpect(jsonPath("$.diferencas.MOVIDO").value(1))
            .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(v2, "$.id");

        mockMvc.perform(get("/api/arvore/versoes"))
            .andExpect(jsonPath("$[?(@.vigente == true)].rotulo").value("qconcursos-2026-10-19"));
        var diff = mockMvc.perform(get("/api/arvore/versoes/{id}/diferenca", id))
            .andReturn().getResponse().getContentAsString();
        List<String> tipos = JsonPath.read(diff, "$[*].tipo");
        assertEquals(List.of("MOVIDO", "NOVO", "REMOVIDO", "RENOMEADO"), tipos);
        assertEquals(List.of("Window Function"), JsonPath.<List<String>>read(diff, "$[?(@.tipo == 'RENOMEADO')].nomeAnterior"));
        assertEquals(List.of(12), JsonPath.<List<Integer>>read(diff, "$[?(@.tipo == 'MOVIDO')].paiAnteriorId"));
    }

    @Test
    void busca_semAcentoPorNomeEPorCaminho_eNuncaDevolveDisciplina() throws Exception {
        carregar(V1);
        var porNome = mockMvc.perform(get("/api/arvore/busca").param("q", "algebra"))
            .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(122), JsonPath.<List<Integer>>read(porNome, "$[*].id"));

        var porCaminho = mockMvc.perform(get("/api/arvore/busca").param("q", "sql window"))
            .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(121), JsonPath.<List<Integer>>read(porCaminho, "$[*].id"));
        assertEquals(List.of("Banco de Dados", "SQL", "Window Function"), JsonPath.<List<String>>read(porCaminho, "$[0].caminho"));

        var disciplina = mockMvc.perform(get("/api/arvore/busca").param("q", "antropologia"))
            .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(5), JsonPath.<List<Integer>>read(disciplina, "$[*].id"),
            "a disciplina sozinha não é alvo de ligação; só o filho, pelo caminho");
    }

    @Test
    void navegacaoPorRamos_raizesEFilhos() throws Exception {
        carregar(V1);
        mockMvc.perform(get("/api/arvore/raizes"))
            .andExpect(jsonPath("$[0].id").value(-10))
            .andExpect(jsonPath("$[1].id").value(-273));
        mockMvc.perform(get("/api/arvore/nos/{id}/filhos", -10))
            .andExpect(jsonPath("$[0].id").value(273))
            .andExpect(jsonPath("$[1].id").value(12))
            .andExpect(jsonPath("$[1].filhos").value(2));
        mockMvc.perform(get("/api/arvore/nos/{id}", 99999))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("NO_INEXISTENTE"));
    }

    @Test
    void semArvoreCarregada_404() throws Exception {
        mockMvc.perform(get("/api/arvore/raizes"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("ARVORE_NAO_CARREGADA"));
    }

    @Test
    void rotaInterna_exigeOToken() throws Exception {
        mockMvc.perform(post("/internal/arvores").contentType(MediaType.APPLICATION_JSON).content(V1))
            .andExpect(status().isUnauthorized());
        assertFalse(jdbc.queryForObject("SELECT count(*) FROM arvore_versao", Long.class) > 0, "nada foi gravado sem o token");
    }

    @Test
    void arvoreInvalida_422() throws Exception {
        mockMvc.perform(internoPost("/internal/arvores", "{\"fonte\": \"qconcursos\", \"data\": \"2026-10-19\", \"disciplinas\": []}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("ARVORE_INVALIDA"));
        var repetido = "{\"fonte\": \"qconcursos\", \"data\": \"2026-10-19\", \"disciplinas\": [{\"id\": 1, \"nome\": \"A\", \"filhos\": ["
            + "{\"id\": 7, \"nome\": \"X\", \"filhos\": []}, {\"id\": 7, \"nome\": \"Y\", \"filhos\": []}]}]}";
        mockMvc.perform(internoPost("/internal/arvores", repetido))
            .andExpect(status().isUnprocessableEntity());
    }
}
