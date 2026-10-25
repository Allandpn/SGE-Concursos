package br.com.estudos.conferencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Atualizar a árvore é gesto da pessoa (01_DOMINIO §3.12, D-90; docs/SPRINT-25-CONFERENCIA-EDITAL.md §8): o impacto vem
 * antes; adotar devolve à fila a parte cujo nó saiu ou mudou de pai e nunca troca um nó sozinho.
 * V1 → V2: 121 renomeado, 5 removido, 122 movido, 6 novo.
 */
class AdocaoArvoreTest extends ConferenciaTestBase {

    @Autowired private AnaliseEditalRepository analises;

    private long id;
    private long versao2;

    @BeforeEach
    void preparar() throws Exception {
        var analise = new AnaliseEdital("edital.pdf", "texto", "modelo");
        analise.setConcursoNome("MPPR Árvore " + System.nanoTime());
        analise.setCargoEscolhido("Analista TI");
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        id = analises.save(analise).getId();
        mockMvc.perform(internoPost("/internal/arvores", ArvoreTest.V1)).andExpect(status().isOk());
        versao2 = ((Number) JsonPath.read(mockMvc.perform(internoPost("/internal/arvores", ArvoreTest.V2)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", EntregaEditalTest.entrega("qconcursos-2026-10-19", 121), id))
            .andExpect(status().isOk());
        // a pessoa liga a p2 ao nó 122 (que na V2 muda de pai)
        long p2 = jdbc.queryForObject("SELECT id FROM edital_parte WHERE chave = 'p2p2p2p2p2p2p2p2'", Long.class);
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", id, p2).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nos\": [122], \"natureza\": \"EXATO\"}")).andExpect(status().isOk());
    }

    private long parteId(String chave) {
        return jdbc.queryForObject("SELECT id FROM edital_parte WHERE chave = ?", Long.class, chave);
    }

    @Test
    void impacto_mostraAntesDeAdotar_eNadaMuda() throws Exception {
        mockMvc.perform(get("/api/arvore/versoes/{v}/impacto", versao2))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.partesAfetadas").value(2))          // p2 (122 mudou de pai) e p4 (5 saiu)
            .andExpect(jsonPath("$.porTipo.MOVIDO").value(1))
            .andExpect(jsonPath("$.porTipo.REMOVIDO").value(1))
            .andExpect(jsonPath("$.partesRenomeadas").value(1))        // p1: 121 só mudou de nome
            .andExpect(jsonPath("$.decisoesAfetadas").value(1))        // a decisão da p2
            .andExpect(jsonPath("$.adotada").value(false));
        mockMvc.perform(post("/api/arvore/versoes/{v}/adotar", versao2)).andExpect(jsonPath("$.adotada").value(false));
        assertEquals("qconcursos-2026-10-19", jdbc.queryForObject("SELECT rotulo FROM arvore_versao WHERE vigente", String.class),
            "sem confirmar a versão vigente não muda");
    }

    @Test
    void adotar_devolveAFilaAParteCujoNoSaiuEMovido_eSegueAsOutras() throws Exception {
        mockMvc.perform(post("/api/arvore/versoes/{v}/adotar", versao2).param("confirmar", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.adotada").value(true));
        assertEquals("qconcursos-2026-11-20", jdbc.queryForObject("SELECT rotulo FROM arvore_versao WHERE vigente", String.class));

        var fila = mockMvc.perform(get("/api/editais/analises/{id}/conferencia/fila", id)).andReturn().getResponse().getContentAsString();
        List<String> tipos = JsonPath.read(fila, "$[?(@.tipo == 'NO_MUDOU_NA_ARVORE')].parte");
        assertEquals(2, tipos.size());

        // a p1 não foi tocada: foi para a versão nova e o nome novo aparece
        mockMvc.perform(get("/api/editais/analises/{id}/conferencia/disciplinas", id))
            .andExpect(jsonPath("$[0].assuntos[0].partes[0].nos[0].nome").value("Funções de Janela"))
            .andExpect(jsonPath("$[0].assuntos[0].partes[0].noMudou").value(false));
        assertEquals(versao2, jdbc.queryForObject("SELECT arvore_versao_id FROM edital_parte WHERE chave = 'p1p1p1p1p1p1p1p1'", Long.class));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM edital_evento WHERE tipo = 'ARVORE_ADOTADA'", Integer.class) >= 1);

        // a decisão da p2 pede reconfirmação
        mockMvc.perform(get("/api/decisoes")).andExpect(jsonPath("$[0].precisaReconfirmar").value(true));
    }

    @Test
    void reconfirmar_noQueAindaExiste_passa_noQueSaiu_pedeOutro() throws Exception {
        mockMvc.perform(post("/api/arvore/versoes/{v}/adotar", versao2).param("confirmar", "true")).andExpect(status().isOk());
        long p2 = parteId("p2p2p2p2p2p2p2p2");
        long p4 = parteId("p4p4p4p4p4p4p4p4");

        mockMvc.perform(post("/api/editais/analises/{id}/partes/{p}/aceitar", id, p2)).andExpect(status().isOk())
            .andExpect(jsonPath("$.partes[1].noMudou").value(false)).andExpect(jsonPath("$.partes[1].estado").value("ACEITA"));
        mockMvc.perform(get("/api/decisoes")).andExpect(jsonPath("$[0].precisaReconfirmar").value(false));

        mockMvc.perform(post("/api/editais/analises/{id}/partes/{p}/aceitar", id, p4))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("NO_MUDOU"));
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", id, p4).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nos\": [6], \"natureza\": \"MAIS_AMPLO\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.partes[0].noMudou").value(false))
            .andExpect(jsonPath("$.partes[0].nos[0].nome").value("CTE"));
        var tipos = JsonPath.<List<String>>read(mockMvc.perform(get("/api/editais/analises/{id}/conferencia/fila", id))
            .andReturn().getResponse().getContentAsString(), "$[*].tipo");
        assertTrue(!tipos.contains("NO_MUDOU_NA_ARVORE"));
    }

    @Test
    void adotarAVigente_naoFazNada() throws Exception {
        long vigente = jdbc.queryForObject("SELECT id FROM arvore_versao WHERE vigente", Long.class);
        mockMvc.perform(post("/api/arvore/versoes/{v}/adotar", vigente).param("confirmar", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.partesAfetadas").value(0));
    }
}
