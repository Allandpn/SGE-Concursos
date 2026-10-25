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
 * Reprocessar preservando a decisão (01_DOMINIO §3.12, D-86, D-89, D-95; docs/SPRINT-26 §3): a segunda leitura se funde na
 * conferência pela chave; decidida ou aceita nunca muda; o que sumiu é marcado, não removido; o que a pessoa criou fica.
 */
class ReprocessarTest extends ConferenciaTestBase {

    @Autowired private AnaliseEditalRepository analises;

    private long id;

    /** A segunda leitura: p1 com nó outro; p2 com nó outro e exato; p3 e o assunto a2 somem; p4 igual; p5 nova; um trecho igual. */
    static String segundaLeitura() {
        return """
            {"contrato": "edital-revisao/1", "concurso": {"slug": "mppr-2024", "cargo": "Analista TI"}, "pdf": "mppr",
             "execucao": {"data": "2026-10-25", "arvore": "qconcursos-2026-10-19", "modelo": "(proxy do SGE)"},
             "disciplinas": [
               {"nome": "Banco de Dados", "qconcursos": [-10], "tentativas": 1,
                "texto": "Banco de Dados SQL: window function, álgebra relacional Arquitetura de BD",
                "assuntos": [
                  {"chave": "a1a1a1a1a1a1a1a1", "ordem": 1, "texto": "SQL: window function, álgebra relacional", "erros": [], "alertas": [],
                   "partes": [
                     {"chave": "p1p1p1p1p1p1p1p1", "ordem": 1, "texto": "window function", "contexto": null, "herdado": "SQL",
                      "ligacao": {"nos": [12], "natureza": "mais_amplo", "origem": "ia"}},
                     {"chave": "p2p2p2p2p2p2p2p2", "ordem": 2, "texto": "álgebra relacional", "contexto": null, "herdado": null,
                      "ligacao": {"nos": [122], "natureza": "exato", "origem": "ia"}},
                     {"chave": "p5p5p5p5p5p5p5p5", "ordem": 3, "texto": "projeção", "contexto": null, "herdado": null,
                      "ligacao": {"nos": [122], "natureza": "parcial", "origem": "ia"}}]}],
                "trechos_sem_assunto": ["Observação solta do edital"], "referencias": []},
               {"nome": "Antropologia", "restricao": "Cargo B", "comum": true, "qconcursos": [-273], "tentativas": 2,
                "texto": "Antropologia Cultura",
                "assuntos": [
                  {"chave": "a3a3a3a3a3a3a3a3", "ordem": 1, "texto": "Cultura", "erros": [], "alertas": ["contexto de outro assunto"],
                   "partes": [
                     {"chave": "p4p4p4p4p4p4p4p4", "ordem": 1, "texto": "Cultura", "contexto": null, "herdado": null,
                      "ligacao": {"nos": [5], "natureza": "exato", "origem": "decisao"}}]}],
                "trechos_sem_assunto": [], "referencias": []}],
             "linhas_fora": [{"linha_inicio": 1, "linha_fim": 9, "paginas": [1, 2], "linhas": ["ANEXO II", "Observações"]}],
             "resumo": {}}""";
    }

    @BeforeEach
    void preparar() throws Exception {
        var analise = new AnaliseEdital("edital.pdf", "texto", "modelo");
        analise.setConcursoNome("MPPR Reprocessar " + System.nanoTime());
        analise.setCargoEscolhido("Analista TI");
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        id = analises.save(analise).getId();
        mockMvc.perform(internoPost("/internal/arvores", ArvoreTest.V1)).andExpect(status().isOk());
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", EntregaEditalTest.entrega("qconcursos-2026-10-19", 121), id)).andExpect(status().isOk());
    }

    private long parteId(String chave) {
        return jdbc.queryForObject("SELECT id FROM edital_parte WHERE chave = ?", Long.class, chave);
    }

    @Test
    void reprocessar_fundePelaChave_semTocarNoQueAPessoaDecidiu() throws Exception {
        long p1 = parteId("p1p1p1p1p1p1p1p1");
        long p2 = parteId("p2p2p2p2p2p2p2p2");
        mockMvc.perform(post("/api/editais/analises/{id}/partes/{p}/aceitar", id, p1)).andExpect(status().isOk());
        // a pessoa edita a p2: nasce uma parte dela (sem execução), a antiga fica descartada
        var editada = mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}", id, p2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"texto\": \"álgebra relacional\", \"contexto\": \"SQL\"}")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(JsonPath.<List<Boolean>>read(editada, "$.partes[?(@.origem == 'PESSOA')].aparece").contains(true));
        int trechos = jdbc.queryForObject("SELECT count(*) FROM edital_trecho", Integer.class);

        mockMvc.perform(post("/api/editais/analises/{id}/conferencia/reprocessar", id)).andExpect(status().isOk());
        assertEquals(StatusAnaliseEdital.SEPARANDO_ASSUNTOS, analises.findById(id).orElseThrow().getStatus());
        mockMvc.perform(interno(get("/internal/editais/pendentes")))
            .andExpect(jsonPath("$[?(@.id == %d)].reprocessar".formatted(id)).value(true));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_evento WHERE tipo = 'REPROCESSAR'", Integer.class));

        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", segundaLeitura(), id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.partesNovas").value(1))        // p5
            .andExpect(jsonPath("$.partesQueSumiram").value(1))   // p3 (e o assunto a2)
            .andExpect(jsonPath("$.iaMudou").value(1));           // p1 aceita; a IA agora propõe o nó 12

        // D-86: a p1 aceita não mudou; a proposta nova virou sugestão; D-95: ela conta em "a IA mudou"
        assertEquals("ACEITA", jdbc.queryForObject("SELECT estado FROM edital_parte WHERE id = ?", String.class, p1));
        assertEquals(List.of(121L), jdbc.queryForList("SELECT no_id FROM edital_parte_no WHERE parte_id = ? AND NOT proposto", Long.class, p1));
        assertEquals(List.of(12L), jdbc.queryForList("SELECT no_id FROM edital_parte_no WHERE parte_id = ? AND proposto", Long.class, p1));
        assertTrue(jdbc.queryForObject("SELECT ia_mudou FROM edital_parte WHERE id = ?", Boolean.class, p1));
        // a p2 antiga continua descartada; a parte da pessoa continua
        assertEquals("DESCARTADA", jdbc.queryForObject("SELECT estado FROM edital_parte WHERE id = ?", String.class, p2));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_parte WHERE origem = 'PESSOA' AND aparece AND execucao_id IS NULL", Integer.class));
        // D-89: o que sumiu é marcado, nunca removido
        assertEquals(false, jdbc.queryForObject("SELECT aparece FROM edital_parte WHERE chave = 'p3p3p3p3p3p3p3p3'", Boolean.class));
        assertEquals(false, jdbc.queryForObject("SELECT aparece FROM edital_assunto WHERE chave = 'a2a2a2a2a2a2a2a2'", Boolean.class));
        // a parte nova entra por conferir; o trecho igual não duplica
        assertEquals("PROPOSTA", jdbc.queryForObject("SELECT estado FROM edital_parte WHERE chave = 'p5p5p5p5p5p5p5p5'", String.class));
        assertEquals(trechos, jdbc.queryForObject("SELECT count(*) FROM edital_trecho", Integer.class));
        // a parte de decisão anterior segue decidida
        assertEquals("DECIDIDA", jdbc.queryForObject("SELECT estado FROM edital_parte WHERE chave = 'p4p4p4p4p4p4p4p4'", String.class));

        assertEquals(StatusAnaliseEdital.EM_CONFERENCIA, analises.findById(id).orElseThrow().getStatus());
        assertEquals(false, analises.findById(id).orElseThrow().isReprocessar());

        // D-95: o cabeçalho conta as decisões mantidas e a fila mostra o novo e o que sumiu
        mockMvc.perform(get("/api/editais/analises/{id}/conferencia", id)).andExpect(jsonPath("$.iaMudou").value(1));
        var tipos = JsonPath.<List<String>>read(mockMvc.perform(get("/api/editais/analises/{id}/conferencia/fila", id))
            .andReturn().getResponse().getContentAsString(), "$[*].tipo");
        assertTrue(tipos.contains("NAO_APARECE_MAIS"), tipos.toString());
        assertTrue(tipos.contains("VINCULO_AMPLO_OU_PARCIAL"), "a p5 nova e parcial");
    }

    @Test
    void parteSoPorConferir_recebeALeituraNova() throws Exception {
        long p2 = parteId("p2p2p2p2p2p2p2p2");   // veio do refino, mais ampla, sem decisão
        mockMvc.perform(post("/api/editais/analises/{id}/conferencia/reprocessar", id)).andExpect(status().isOk());
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", segundaLeitura(), id)).andExpect(status().isOk());
        assertEquals("EXATO", jdbc.queryForObject("SELECT natureza FROM edital_parte WHERE id = ?", String.class, p2));
        assertEquals("IA", jdbc.queryForObject("SELECT origem FROM edital_parte WHERE id = ?", String.class, p2));
        assertEquals(List.of(122L), jdbc.queryForList("SELECT no_id FROM edital_parte_no WHERE parte_id = ? AND NOT proposto", Long.class, p2));
    }

    @Test
    void reprocessarSoComAConferenciaAberta_eAMesmaLeituraNaoDuplica() throws Exception {
        mockMvc.perform(post("/api/editais/analises/{id}/conferencia/reprocessar", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/editais/analises/{id}/conferencia/reprocessar", id))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ETAPA_INVALIDA")); // já está esperando o worker
        var corpo = segundaLeitura();
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", corpo, id)).andExpect(status().isOk());
        int partes = jdbc.queryForObject("SELECT count(*) FROM edital_parte", Integer.class);
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", corpo, id))
            .andExpect(status().isOk()).andExpect(jsonPath("$.repetida").value(true));
        assertEquals(partes, jdbc.queryForObject("SELECT count(*) FROM edital_parte", Integer.class));
    }
}
