package br.com.estudos.conferencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import br.com.estudos.arvore.ArvoreTest;
import br.com.estudos.edital.AnaliseEdital;
import br.com.estudos.edital.AnaliseEditalRepository;
import br.com.estudos.shared.ConferenciaTestBase;
import br.com.estudos.shared.enums.StatusAnaliseEdital;

/**
 * O material por casamento no SGE (01_DOMINIO §3.10, D-65, D-94; docs/SPRINT-26 §6): o que espera material, o que o planejamento
 * entrega e como a tela e o planejamento enxergam isso.
 */
class MaterialConferenciaTest extends ConferenciaTestBase {

    @Autowired private AnaliseEditalRepository analises;

    private long id;
    private long concursoId;

    @BeforeEach
    void concluir() throws Exception {
        var analise = new AnaliseEdital("edital.pdf", "texto", "modelo");
        analise.setConcursoNome("MPPR Material " + System.nanoTime());
        analise.setCargoEscolhido("Analista TI");
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        id = analises.save(analise).getId();
        mockMvc.perform(internoPost("/internal/arvores", ArvoreTest.V1)).andExpect(status().isOk());
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", EntregaEditalTest.entrega("qconcursos-2026-10-19", 121), id)).andExpect(status().isOk());
        for (long a : jdbc.queryForList("SELECT id FROM edital_assunto", Long.class)) {
            mockMvc.perform(post("/api/editais/analises/{id}/assuntos/{a}/aceitar", id, a)).andExpect(status().isOk());
        }
        for (long t : jdbc.queryForList("SELECT id FROM edital_trecho", Long.class)) {
            mockMvc.perform(post("/api/editais/analises/{id}/trechos/{t}/resolver", id, t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\": \"DESCARTAR\", \"motivo\": \"observação\"}")).andExpect(status().isOk());
        }
        concursoId = ((Number) JsonPath.read(mockMvc.perform(post("/api/editais/analises/{id}/conferencia/concluir", id))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.concursoId")).longValue();
    }

    private String entrega(String chave, String situacao) {
        return """
            {"assuntos": [{"chave": "%s", "situacao": "%s", "minutos": 90, "fontes": 2,
              "lacunas": [{"parte": "álgebra relacional", "motivo": "nenhum tema do acervo ensina esta parte"}],
              "consulta": {"arquivo": "consulta/%s.pdf", "paginas": 12}}]}""".formatted(chave, situacao, chave);
    }

    @Test
    void pendentes_soDeConferenciaConcluida_comAsPartesQueAPessoaConferiu() throws Exception {
        // outro edital ainda em conferência não entra
        var aberta = new AnaliseEdital("outro.pdf", "texto", "modelo");
        aberta.setStatus(StatusAnaliseEdital.EM_CONFERENCIA);
        analises.save(aberta);

        var resposta = mockMvc.perform(interno(get("/internal/editais/material/pendentes")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].analiseId").value(id))
            .andExpect(jsonPath("$[0].assuntos.length()").value(3))
            .andReturn().getResponse().getContentAsString();
        List<String> chavesExternas = JsonPath.read(resposta, "$[0].assuntos[*].chaveExterna");
        assertTrue(chavesExternas.stream().allMatch(c -> c != null && c.length() == 36));
        assertEquals(List.of(false, false, false), JsonPath.<List<Boolean>>read(resposta, "$[0].assuntos[*].temSegmentos"));
        assertEquals(List.of("window function", "álgebra relacional"), JsonPath.<List<String>>read(resposta, "$[0].assuntos[0].partes[*].texto"));
        assertEquals(List.of(121), JsonPath.<List<Integer>>read(resposta, "$[0].assuntos[0].partes[0].nos"));
    }

    @Test
    void entregar_gravaASituacao_tiraOAssuntoDosPendentes_eAparecemNaTela() throws Exception {
        mockMvc.perform(internoPost("/internal/editais/{id}/material", entrega("a1a1a1a1a1a1a1a1", "PARCIAL"), id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.atualizados").value(1));
        mockMvc.perform(internoPost("/internal/editais/{id}/material", entrega("a1a1a1a1a1a1a1a1", "PARCIAL"), id))
            .andExpect(status().isOk()); // idempotente

        mockMvc.perform(interno(get("/internal/editais/material/pendentes"))).andExpect(jsonPath("$[0].assuntos.length()").value(2));

        var tela = mockMvc.perform(get("/api/concursos/{c}/material", concursoId)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var filtro = "$[?(@.assunto == 'SQL: window function, álgebra relacional')]";
        assertEquals(List.of("PARCIAL"), JsonPath.<List<String>>read(tela, filtro + ".situacaoDoCasamento"));
        assertEquals(List.of(90), JsonPath.<List<Integer>>read(tela, filtro + ".minutosEstimados"));
        assertEquals(List.of("consulta/a1a1a1a1a1a1a1a1.pdf"), JsonPath.<List<String>>read(tela, filtro + ".consulta"));
        assertEquals(List.of(List.of("álgebra relacional: nenhum tema do acervo ensina esta parte")),
            JsonPath.<List<List<String>>>read(tela, filtro + ".lacunasPorParte"));
    }

    @Test
    void entregaInvalida_assuntoAlheio404_situacaoDesconhecida422() throws Exception {
        mockMvc.perform(internoPost("/internal/editais/{id}/material", entrega("0000000000000000", "COMPLETO"), id))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("ASSUNTO_INEXISTENTE"));
        mockMvc.perform(internoPost("/internal/editais/{id}/material", entrega("a1a1a1a1a1a1a1a1", "INVENTADA"), id))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("SITUACAO_INVALIDA"));
    }

    @Test
    void oPlanejamentoSabeQueOAssuntoVemDeConferencia_eAntigoNaoMexe() throws Exception {
        var entrega = mockMvc.perform(get("/api/concursos/{c}/planejamento", concursoId)).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(true, true, true), JsonPath.<List<Boolean>>read(entrega, "$.assuntos[*].viaConferencia"));
    }

    @Test
    void assuntoComLacunaJaRegistrada_naoEntraNosPendentes() throws Exception {
        var entrega = mockMvc.perform(get("/api/concursos/{c}/planejamento", concursoId)).andReturn().getResponse().getContentAsString();
        String chaveExterna = JsonPath.read(entrega, "$.assuntos[2].chaveExterna");
        mockMvc.perform(internoPost("/internal/material/lacunas", "{\"chaveExterna\": \"" + chaveExterna + "\", \"motivo\": \"o acervo não ensina\"}"))
            .andExpect(status().is2xxSuccessful());
        mockMvc.perform(interno(get("/internal/editais/material/pendentes"))).andExpect(jsonPath("$[0].assuntos.length()").value(2));
    }
}
