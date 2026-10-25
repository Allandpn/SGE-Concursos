package br.com.estudos.conferencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
 * Decisão para todos os editais (01_DOMINIO §3.12, D-86, D-87; docs/SPRINT-26 §4): pede confirmação extra com a contagem do
 * que mudaria em outros editais, nunca toca parte que a pessoa já decidiu e vai nos dados de qualquer edital novo.
 */
class EscopoTodosTest extends ConferenciaTestBase {

    @Autowired private AnaliseEditalRepository analises;

    private long a;   // o edital em que a pessoa decide
    private long b;   // outro edital, com a mesma parte ainda por conferir

    private long novaAnalise(String nome, StatusAnaliseEdital status) {
        var x = new AnaliseEdital(nome + ".pdf", "texto", "modelo");
        x.setConcursoNome(nome + " " + System.nanoTime());
        x.setCargoEscolhido("Analista TI");
        x.setStatus(status);
        return analises.save(x).getId();
    }

    private long parteId(long analise, String chave) {
        return jdbc.queryForObject("""
            SELECT p.id FROM edital_parte p JOIN edital_assunto s ON s.id = p.assunto_edital_id
              JOIN edital_disciplina d ON d.id = s.disciplina_id WHERE d.analise_id = ? AND p.chave = ?""", Long.class, analise, chave);
    }

    @BeforeEach
    void preparar() throws Exception {
        a = novaAnalise("Edital A", StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        b = novaAnalise("Edital B", StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        mockMvc.perform(internoPost("/internal/arvores", ArvoreTest.V1)).andExpect(status().isOk());
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", EntregaEditalTest.entrega("qconcursos-2026-10-19", 121), a)).andExpect(status().isOk());
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", ReprocessarTest.segundaLeitura(), b)).andExpect(status().isOk());
    }

    private String nosTodos(boolean confirmar) {
        return "{\"nos\": [121], \"natureza\": \"EXATO\", \"escopo\": \"TODOS\", \"confirmarTodos\": " + confirmar + "}";
    }

    @Test
    void previa_contaAsPartesDeOutrosEditaisQueMudariam() throws Exception {
        long p1 = parteId(a, "p1p1p1p1p1p1p1p1");
        mockMvc.perform(get("/api/editais/analises/{id}/partes/{p}/escopo-todos", a, p1).param("nos", "121").param("natureza", "EXATO"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.partes").value(1))
            .andExpect(jsonPath("$.exemplos[0]").value(org.hamcrest.Matchers.containsString("window function")));
    }

    @Test
    void semConfirmar_409ComAContagem_econfirmado_ligaAsPartesPorConferirDeOutrosEditais() throws Exception {
        long p1 = parteId(a, "p1p1p1p1p1p1p1p1");
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", a, p1).contentType(MediaType.APPLICATION_JSON).content(nosTodos(false)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("CONFIRMACAO_TODOS"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 parte")));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM edital_decisao", Integer.class), "sem confirmação nada é gravado");

        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", a, p1).contentType(MediaType.APPLICATION_JSON).content(nosTodos(true)))
            .andExpect(status().isOk());
        long noB = parteId(b, "p1p1p1p1p1p1p1p1");
        assertEquals("DECIDIDA", jdbc.queryForObject("SELECT estado FROM edital_parte WHERE id = ?", String.class, noB));
        assertEquals("DECISAO", jdbc.queryForObject("SELECT origem FROM edital_parte WHERE id = ?", String.class, noB));
        assertEquals(List.of(121L), jdbc.queryForList("SELECT no_id FROM edital_parte_no WHERE parte_id = ? AND NOT proposto", Long.class, noB));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_decisao WHERE escopo = 'TODOS' AND ativa", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_evento WHERE tipo = 'DECISAO_TODOS' AND analise_id = ?", Integer.class, b));
    }

    @Test
    void parteQueAPessoaJaDecidiuEmOutroEdital_nuncaEhTocada_d86() throws Exception {
        long pb = parteId(b, "p1p1p1p1p1p1p1p1");
        mockMvc.perform(post("/api/editais/analises/{id}/partes/{p}/aceitar", b, pb)).andExpect(status().isOk()); // B: aceita o nó 12
        long p1 = parteId(a, "p1p1p1p1p1p1p1p1");
        mockMvc.perform(get("/api/editais/analises/{id}/partes/{p}/escopo-todos", a, p1).param("nos", "121").param("natureza", "EXATO"))
            .andExpect(jsonPath("$.partes").value(0));
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", a, p1).contentType(MediaType.APPLICATION_JSON).content(nosTodos(true)))
            .andExpect(status().isOk());
        assertEquals(List.of(12L), jdbc.queryForList("SELECT no_id FROM edital_parte_no WHERE parte_id = ? AND NOT proposto", Long.class, pb),
            "a decisão da pessoa em B continua");
        assertEquals("ACEITA", jdbc.queryForObject("SELECT estado FROM edital_parte WHERE id = ?", String.class, pb));
    }

    @Test
    void decisaoParaTodosVaiNosDadosDeQualquerEdital_masADeUmEditalSoNao() throws Exception {
        long p1 = parteId(a, "p1p1p1p1p1p1p1p1");
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", a, p1).contentType(MediaType.APPLICATION_JSON).content(nosTodos(true))).andExpect(status().isOk());
        long p2 = parteId(a, "p2p2p2p2p2p2p2p2");
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", a, p2).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nos\": [122], \"natureza\": \"EXATO\"}")).andExpect(status().isOk()); // só este edital

        long c = novaAnalise("Edital C", StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        mockMvc.perform(interno(get("/internal/editais/{id}/dados", c)))
            .andExpect(jsonPath("$.decisoes.length()").value(1))
            .andExpect(jsonPath("$.decisoes[0].escopo").value("TODOS"))
            .andExpect(jsonPath("$.decisoes[0].nos[0]").value(121));
        jdbc.update("UPDATE analise_edital SET status = 'SEPARANDO_ASSUNTOS' WHERE id = ?", a);
        mockMvc.perform(interno(get("/internal/editais/{id}/dados", a)))
            .andExpect(jsonPath("$.decisoes.length()").value(2));
    }

    @Test
    void promoverAParaTodos_substituiADecisaoSoDesteEdital() throws Exception {
        long p1 = parteId(a, "p1p1p1p1p1p1p1p1");
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", a, p1).contentType(MediaType.APPLICATION_JSON)
            .content("{\"nos\": [121], \"natureza\": \"EXATO\"}")).andExpect(status().isOk());
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", a, p1).contentType(MediaType.APPLICATION_JSON).content(nosTodos(true))).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_decisao WHERE ativa", Integer.class));
        assertEquals("TODOS", jdbc.queryForObject("SELECT escopo FROM edital_decisao WHERE ativa", String.class));
    }

    @Test
    void escopoDesconhecido_422() throws Exception {
        long p1 = parteId(a, "p1p1p1p1p1p1p1p1");
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", a, p1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nos\": [121], \"natureza\": \"EXATO\", \"escopo\": \"MUNDO\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("ESCOPO_INVALIDO"));
    }
}
