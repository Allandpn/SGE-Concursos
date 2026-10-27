package br.com.estudos.conferencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.concurso.ConcursoRepository;
import br.com.estudos.edital.AnaliseEdital;
import br.com.estudos.edital.AnaliseEditalRepository;
import br.com.estudos.shared.ConferenciaTestBase;
import br.com.estudos.shared.enums.StatusAnaliseEdital;

/**
 * A tela de conferência (01_DOMINIO §3.12, D-86 a D-92; docs/SPRINT-25-CONFERENCIA-EDITAL.md §5 a §7), com a entrega
 * de {@link EntregaEditalTest#entrega}: 4 partes (p1 exata, p2 mais ampla vinda do refino, p3 sem nó, p4 vinda de decisão),
 * um assunto recusado pela regra, um trecho sem assunto e um bloco de linhas fora de disciplina.
 */
class ConferenciaTest extends ConferenciaTestBase {

    @Autowired private AnaliseEditalRepository analises;
    @Autowired private ConcursoRepository concursos;
    @Autowired private AssuntoRepository assuntos;
    @Autowired private br.com.estudos.arvore.ArvoreService arvore;

    private long id;

    @BeforeEach
    void entregar() throws Exception {
        var analise = new AnaliseEdital("edital.pdf", "texto do edital", "modelo");
        analise.setConcursoNome("MPPR Conferência " + System.nanoTime());
        analise.setCargoEscolhido("Analista TI");
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        id = analises.save(analise).getId();
        mockMvc.perform(internoPost("/internal/arvores", ArvoreTest.V1)).andExpect(status().isOk());
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", EntregaEditalTest.entrega("qconcursos-2026-10-19", 121), id))
            .andExpect(status().isOk());
    }

    private long parteId(String chave) {
        return jdbc.queryForObject("SELECT id FROM edital_parte WHERE chave = ?", Long.class, chave);
    }

    private String putJson(String url, String json, Object... v) throws Exception {
        return mockMvc.perform(put(url, v).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String postJson(String url, String json, Object... v) throws Exception {
        return mockMvc.perform(post(url, v).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    @Test
    void resumo_contagensEPendencias() throws Exception {
        mockMvc.perform(get("/api/editais/analises/{id}/conferencia", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disciplinas").value(2))
            .andExpect(jsonPath("$.assuntos").value(3))
            .andExpect(jsonPath("$.partes").value(4))
            .andExpect(jsonPath("$.porNatureza.EXATO").value(2))
            .andExpect(jsonPath("$.porNatureza.MAIS_AMPLO").value(1))
            .andExpect(jsonPath("$.semNo").value(1))
            .andExpect(jsonPath("$.aplicadasDeDecisaoAnterior").value(1))
            .andExpect(jsonPath("$.decididasPorPessoa").value(0))
            // bloqueiam: linha fora, trecho sem assunto, assunto recusado, alerta do assunto 3; avisam: sem nó, amplo, refino
            .andExpect(jsonPath("$.pendencias.bloqueiam").value(4))
            .andExpect(jsonPath("$.podeConcluir").value(false));
    }

    @Test
    void fila_bloqueantesPrimeiro_depoisAGravidade() throws Exception {
        var fila = mockMvc.perform(get("/api/editais/analises/{id}/conferencia/fila", id)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> tipos = JsonPath.read(fila, "$[*].tipo");
        assertEquals(List.of("LINHA_FORA", "SEM_ASSUNTO", "ASSUNTO_RECUSADO", "ALERTA_DE_PARTE", "PARTE_SEM_NO", "NO_TROCADO_NO_REFINO"), tipos,
            "p1 exata e p4 de decisão não entram; a p2 é mais ampla com nó pequeno (2 filhos): só entra por ter vindo do refino");
        List<Boolean> bloqueia = JsonPath.read(fila, "$[*].bloqueia");
        assertEquals(List.of(true, true, true, true, false, false), bloqueia);
    }

    @Test
    void aceitosSozinhos_listaOQueAFilaNaoPedeComOMotivo() throws Exception {
        var lista = mockMvc.perform(get("/api/editais/analises/{id}/conferencia/aceitos-sozinhos", id)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> partes = JsonPath.read(lista, "$[*].parte");
        assertEquals(1, partes.size(), "só a p1 (exata, por conferir): a p2 é do refino, a p3 não tem nó e a p4 já é decisão");
        assertEquals("Window Function", JsonPath.<String>read(lista, "$[0].nos[0].nome"));
        assertEquals("O QConcursos tem este assunto igual ao do edital.", JsonPath.<String>read(lista, "$[0].motivo"));
    }

    @Test
    void maisAmplaComNoLargo_pedeAPessoa_eComNoPequenoNao() throws Exception {
        long p2 = parteId("p2p2p2p2p2p2p2p2");
        jdbc.update("UPDATE edital_parte SET origem = 'IA' WHERE id = ?", p2);   // sem o refino, só a regra do nó decide
        var antes = JsonPath.<List<String>>read(mockMvc.perform(get("/api/editais/analises/{id}/conferencia/fila", id))
            .andReturn().getResponse().getContentAsString(), "$[*].tipo");
        assertTrue(!antes.contains("VINCULO_AMPLO_OU_PARCIAL"), "mais ampla com 2 filhos entra aceita sozinha: " + antes);
        var aceitos = JsonPath.<List<String>>read(mockMvc.perform(get("/api/editais/analises/{id}/conferencia/aceitos-sozinhos", id))
            .andReturn().getResponse().getContentAsString(), "$[*].motivo");
        assertTrue(aceitos.stream().anyMatch(m -> m.contains("poucos subitens (2)")), aceitos.toString());

        // o mesmo vínculo com um nó largo (acima de 3 filhos) vira exceção
        jdbc.update("""
            INSERT INTO no_arvore (versao_id, no_id, nome, pai_id, ordem, raiz_id)
            SELECT versao_id, 9000 + g, 'Extra ' || g, 12, 10 + g, raiz_id FROM no_arvore, generate_series(1, 3) g WHERE no_id = 12""");
        ((java.util.Map<?, ?>) org.springframework.test.util.ReflectionTestUtils.getField(arvore, "cache")).clear();   // a árvore fica em memória
        var depois = JsonPath.<List<String>>read(mockMvc.perform(get("/api/editais/analises/{id}/conferencia/fila", id))
            .andReturn().getResponse().getContentAsString(), "$[*].tipo");
        assertTrue(depois.contains("VINCULO_AMPLO_OU_PARCIAL"), "mais ampla com 5 filhos pede a pessoa: " + depois);
    }

    @Test
    void arvoreDoEdital_trazOsNosComCaminhoEOsPropostos() throws Exception {
        mockMvc.perform(get("/api/editais/analises/{id}/conferencia/disciplinas", id))
            .andExpect(jsonPath("$[0].nome").value("Banco de Dados"))
            .andExpect(jsonPath("$[0].peso").value("MEDIO"))
            .andExpect(jsonPath("$[0].pesoDeterminado").value(false))
            .andExpect(jsonPath("$[0].assuntos[0].partes[0].nos[0].caminho[2]").value("Window Function"))
            .andExpect(jsonPath("$[0].assuntos[0].partes[1].natureza").value("MAIS_AMPLO"))
            .andExpect(jsonPath("$[0].assuntos[0].partes[1].origem").value("REFINO"))
            .andExpect(jsonPath("$[1].assuntos[0].partes[0].origem").value("DECISAO"));
    }

    @Test
    void aceitar_viraDecisaoPresaAParteEAoContexto_d86() throws Exception {
        long p1 = parteId("p1p1p1p1p1p1p1p1");
        mockMvc.perform(post("/api/editais/analises/{id}/partes/{p}/aceitar", id, p1)).andExpect(status().isOk())
            .andExpect(jsonPath("$.partes[0].estado").value("ACEITA"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_decisao WHERE ativa AND analise_id = ?", Integer.class, id));
        assertEquals("SQL window function", jdbc.queryForObject("SELECT parte_texto FROM edital_decisao", String.class));
        assertEquals("[121]", jdbc.queryForObject("SELECT nos FROM edital_decisao", String.class));
        mockMvc.perform(get("/api/editais/analises/{id}/conferencia", id)).andExpect(jsonPath("$.decididasPorPessoa").value(1));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_evento WHERE tipo = 'ACEITAR'", Integer.class));
    }

    @Test
    void trocarNos_validaOsNos_guardaOQueASistemaPropos_eRegistraDecisao() throws Exception {
        long p2 = parteId("p2p2p2p2p2p2p2p2");
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", id, p2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nos\": [9999], \"natureza\": \"EXATO\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("NO_INEXISTENTE"));
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", id, p2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nos\": [-10], \"natureza\": \"EXATO\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("NO_DE_DISCIPLINA"));

        var resposta = putJson("/api/editais/analises/{id}/partes/{p}/nos", "{\"nos\": [122], \"natureza\": \"EXATO\"}", id, p2);
        assertEquals("DECIDIDA", JsonPath.read(resposta, "$.partes[1].estado"));
        assertEquals("PESSOA", JsonPath.read(resposta, "$.partes[1].origem"));
        assertEquals(122, ((Number) JsonPath.read(resposta, "$.partes[1].nos[0].id")).intValue());
        assertEquals(12, ((Number) JsonPath.read(resposta, "$.partes[1].propostos[0].id")).intValue(), "o que a IA propôs fica como sugestão");
    }

    @Test
    void semNo_declaraQueAArvoreNaoTem_eSaiDaFilaMasContaComoSemNo() throws Exception {
        long p3 = parteId("p3p3p3p3p3p3p3p3");
        mockMvc.perform(post("/api/editais/analises/{id}/partes/{p}/sem-no", id, p3)).andExpect(status().isOk())
            .andExpect(jsonPath("$.partes[0].estado").value("DECIDIDA"));
        var tipos = JsonPath.<List<String>>read(mockMvc.perform(get("/api/editais/analises/{id}/conferencia/fila", id))
            .andReturn().getResponse().getContentAsString(), "$[*].tipo");
        assertTrue(!tipos.contains("PARTE_SEM_NO"));
        mockMvc.perform(get("/api/editais/analises/{id}/conferencia", id)).andExpect(jsonPath("$.semNo").value(1));
    }

    @Test
    void aceitarExatosDaDisciplina_eAceitarAssuntoRecusado_eTrechos_levamAConcluir() throws Exception {
        long disc1 = jdbc.queryForObject("SELECT id FROM edital_disciplina WHERE nome = 'Banco de Dados'", Long.class);
        mockMvc.perform(post("/api/editais/analises/{id}/disciplinas/{d}/aceitar-exatos", id, disc1)).andExpect(jsonPath("$.aceitas").value(1));
        long a2 = jdbc.queryForObject("SELECT id FROM edital_assunto WHERE chave = 'a2a2a2a2a2a2a2a2'", Long.class);
        long a3 = jdbc.queryForObject("SELECT id FROM edital_assunto WHERE chave = 'a3a3a3a3a3a3a3a3'", Long.class);
        mockMvc.perform(post("/api/editais/analises/{id}/assuntos/{a}/aceitar", id, a2)).andExpect(status().isOk());
        mockMvc.perform(post("/api/editais/analises/{id}/assuntos/{a}/aceitar", id, a3)).andExpect(status().isOk());
        for (long t : jdbc.queryForList("SELECT id FROM edital_trecho", Long.class)) {
            postJson("/api/editais/analises/{id}/trechos/{t}/resolver", "{\"acao\": \"DESCARTAR\", \"motivo\": \"observação do edital\"}", id, t);
        }
        mockMvc.perform(get("/api/editais/analises/{id}/conferencia", id))
            .andExpect(jsonPath("$.pendencias.bloqueiam").value(0))
            .andExpect(jsonPath("$.podeConcluir").value(true));
    }

    @Test
    void aceitarAmplos_aceitaSoMaisAmploEParcialDaDisciplina() throws Exception {
        long disc1 = jdbc.queryForObject("SELECT id FROM edital_disciplina WHERE nome = 'Banco de Dados'", Long.class);
        mockMvc.perform(post("/api/editais/analises/{id}/disciplinas/{d}/aceitar-amplos", id, disc1)).andExpect(jsonPath("$.aceitas").value(1)); // a p2
        assertEquals("ACEITA", jdbc.queryForObject("SELECT estado FROM edital_parte WHERE chave = 'p2p2p2p2p2p2p2p2'", String.class));
        assertEquals("PROPOSTA", jdbc.queryForObject("SELECT estado FROM edital_parte WHERE chave = 'p1p1p1p1p1p1p1p1'", String.class), "a exata não entra no atalho dos amplos");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_evento WHERE tipo = 'ACEITAR_AMPLOS'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_decisao WHERE ativa", Integer.class));
    }

    @Test
    void editarParte_criaParteNovaRefazAConferenciaDoAssunto() throws Exception {
        long p1 = parteId("p1p1p1p1p1p1p1p1");
        var resposta = putJson("/api/editais/analises/{id}/partes/{p}", "{\"texto\": \"window functions inventadas\", \"herdado\": \"SQL\"}", id, p1);
        // a parte antiga some da lista viva; a nova usa palavra que não está no assunto e a regra reclama
        List<String> textos = JsonPath.read(resposta, "$.partes[?(@.aparece == true)].texto");
        assertTrue(textos.contains("window functions inventadas") && !textos.contains("window function"));
        List<String> erros = JsonPath.read(resposta, "$.erros");
        assertTrue(erros.stream().anyMatch(e -> e.contains("usa palavras que não estão no assunto")), erros.toString());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_parte WHERE id = ? AND NOT aparece AND estado = 'DESCARTADA'", Integer.class, p1));
    }

    @Test
    void dividirEJuntar_renumeramEMantemOsNos() throws Exception {
        long p2 = parteId("p2p2p2p2p2p2p2p2");
        var dividida = postJson("/api/editais/analises/{id}/partes/{p}/dividir",
            "{\"partes\": [{\"texto\": \"álgebra\", \"contexto\": null}, {\"texto\": \"relacional\"}]}", id, p2);
        List<String> textos = JsonPath.read(dividida, "$.partes[?(@.aparece == true)].texto");
        assertEquals(List.of("window function", "álgebra", "relacional"), textos);
        assertEquals(List.of(12), JsonPath.<List<Integer>>read(dividida, "$.partes[?(@.texto == 'álgebra')].nos[*].id"),
            "cada pedaço herda o nó, para a pessoa só conferir");
        List<Integer> ordens = JsonPath.read(dividida, "$.partes[?(@.aparece == true)].ordem");
        assertEquals(List.of(1, 2, 3), ordens);

        long a = parteIdPorTexto("álgebra");
        long b = parteIdPorTexto("relacional");
        var junta = postJson("/api/editais/analises/{id}/partes/{p}/juntar", "{\"outraParteId\": " + b + ", \"texto\": \"álgebra relacional\"}", id, a);
        List<String> depois = JsonPath.read(junta, "$.partes[?(@.aparece == true)].texto");
        assertEquals(List.of("window function", "álgebra relacional"), depois);
    }

    private long parteIdPorTexto(String texto) {
        return jdbc.queryForObject("SELECT id FROM edital_parte WHERE texto = ? AND aparece", Long.class, texto);
    }

    @Test
    void descartarPedeMotivo_eSomeDaContagem() throws Exception {
        long p3 = parteId("p3p3p3p3p3p3p3p3");
        mockMvc.perform(post("/api/editais/analises/{id}/partes/{p}/descartar", id, p3).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("MOTIVO_OBRIGATORIO"));
        postJson("/api/editais/analises/{id}/partes/{p}/descartar", "{\"motivo\": \"repetição\"}", id, p3);
        mockMvc.perform(get("/api/editais/analises/{id}/conferencia", id)).andExpect(jsonPath("$.partes").value(3));
    }

    @Test
    void decisoes_listarEditarERevogar_d86() throws Exception {
        postJson("/api/editais/analises/{id}/partes/{p}/aceitar", "{}", id, parteId("p1p1p1p1p1p1p1p1"));
        var lista = mockMvc.perform(get("/api/decisoes")).andReturn().getResponse().getContentAsString();
        int decisaoId = JsonPath.read(lista, "$[0].id");
        assertEquals("Window Function", JsonPath.read(lista, "$[0].nos[0].nome"));

        mockMvc.perform(put("/api/decisoes/{d}", decisaoId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nos\": [122], \"natureza\": \"PARCIAL\", \"comentario\": \"melhor assim\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.natureza").value("PARCIAL")).andExpect(jsonPath("$.nos[0].id").value(122));
        mockMvc.perform(delete("/api/decisoes/{d}", decisaoId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/decisoes")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/decisoes").param("ativas", "false")).andExpect(jsonPath("$[0].ativa").value(false));
        // D-86: histórico só cresce
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM edital_evento WHERE tipo IN ('ACEITAR','DECISAO_EDITADA','DECISAO_REVOGADA')", Integer.class) >= 3);
    }

    @Test
    void d87_decisaoDeEscopoEditalVaiNosDadosDoPlanejamento() throws Exception {
        postJson("/api/editais/analises/{id}/partes/{p}/aceitar", "{}", id, parteId("p1p1p1p1p1p1p1p1"));
        jdbc.update("UPDATE analise_edital SET status = 'SEPARANDO_ASSUNTOS' WHERE id = ?", id);
        mockMvc.perform(interno(get("/internal/editais/{id}/dados", id)))
            .andExpect(jsonPath("$.decisoes.length()").value(1))
            .andExpect(jsonPath("$.decisoes[0].parte").value("SQL window function"))
            .andExpect(jsonPath("$.decisoes[0].nos[0]").value(121))
            .andExpect(jsonPath("$.decisoes[0].natureza").value("exato"));
    }

    // ------------------------------------------------------------ concluir (25.7)

    private void resolverTudoQueBloqueia() throws Exception {
        for (long a : jdbc.queryForList("SELECT id FROM edital_assunto", Long.class)) {
            postJson("/api/editais/analises/{id}/assuntos/{a}/aceitar", "{}", id, a);
        }
        for (long t : jdbc.queryForList("SELECT id FROM edital_trecho", Long.class)) {
            postJson("/api/editais/analises/{id}/trechos/{t}/resolver", "{\"acao\": \"DESCARTAR\", \"motivo\": \"observação\"}", id, t);
        }
    }

    @Test
    void concluirComBloqueio_422_enadaEhCriado_d91_d92() throws Exception {
        long antes = concursos.count();
        mockMvc.perform(post("/api/editais/analises/{id}/conferencia/concluir", id))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("CONFERENCIA_COM_BLOQUEIO"));
        assertEquals(antes, concursos.count(), "antes da conclusão não existe concurso (D-92)");
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM edital_assunto WHERE assunto_id IS NOT NULL", Integer.class));
    }

    @Test
    void concluirComParteSemNo_passa_criaOsAssuntosComChave_eAtivaOConcurso() throws Exception {
        resolverTudoQueBloqueia();
        var resposta = mockMvc.perform(post("/api/editais/analises/{id}/conferencia/concluir", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assuntos").value(3))
            .andExpect(jsonPath("$.partesSemNo").value(1))
            .andReturn().getResponse().getContentAsString();
        long concursoId = ((Number) JsonPath.read(resposta, "$.concursoId")).longValue();

        assertEquals(StatusAnaliseEdital.CONFIRMADA, analises.findById(id).orElseThrow().getStatus());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM edital_assunto WHERE assunto_id IS NOT NULL", Integer.class));
        mockMvc.perform(get("/api/concursos/ativo")).andExpect(jsonPath("$.id").value(concursoId));
        var entrega = mockMvc.perform(get("/api/concursos/{id}/planejamento", concursoId)).andReturn().getResponse().getContentAsString();
        List<String> chaves = JsonPath.read(entrega, "$.assuntos[*].chaveExterna");
        assertEquals(3, chaves.size());
        assertTrue(chaves.stream().allMatch(c -> c != null && c.length() == 36), "D-64: todo assunto nasce com chave");
        List<String> nomes = JsonPath.read(entrega, "$.assuntos[*].assunto");
        assertTrue(nomes.contains("Arquitetura de BD") && nomes.contains("Cultura"));
        mockMvc.perform(post("/api/editais/analises/{id}/conferencia/concluir", id))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("JA_CONCLUIDA"));
        assertTrue(assuntos.count() >= 3);
    }

    @Test
    void avisosDepoisDeConcluida_noConcursoENoAssunto_naoBloqueiam() throws Exception {
        resolverTudoQueBloqueia();
        long concursoId = ((Number) JsonPath.read(mockMvc.perform(post("/api/editais/analises/{id}/conferencia/concluir", id))
            .andReturn().getResponse().getContentAsString(), "$.concursoId")).longValue();
        mockMvc.perform(get("/api/concursos/{c}/avisos-de-conferencia", concursoId))
            .andExpect(jsonPath("$.partesSemNo").value(1))          // p3
            .andExpect(jsonPath("$.vinculosAmplosOuParciais").value(1)) // p2
            .andExpect(jsonPath("$.nosQueMudaram").value(0));
        long assuntoId = jdbc.queryForObject("SELECT assunto_id FROM edital_assunto WHERE chave = 'a1a1a1a1a1a1a1a1'", Long.class);
        var partes = mockMvc.perform(get("/api/assuntos/{a}/partes-do-edital", assuntoId)).andReturn().getResponse().getContentAsString();
        assertEquals(List.of("window function", "álgebra relacional"), JsonPath.<List<String>>read(partes, "$[*].texto"));
        assertEquals("MAIS_AMPLO", JsonPath.read(partes, "$[1].aviso"));
        mockMvc.perform(get("/api/assuntos/{a}/partes-do-edital", 999999999)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void depoisDeConcluida_aConferenciaContinuaAberta_d93() throws Exception {
        resolverTudoQueBloqueia();
        mockMvc.perform(post("/api/editais/analises/{id}/conferencia/concluir", id)).andExpect(status().isOk());
        long p3 = parteId("p3p3p3p3p3p3p3p3");
        int segmentos = jdbc.queryForObject("SELECT count(*) FROM segmento", Integer.class);
        int sessoes = jdbc.queryForObject("SELECT count(*) FROM sessao", Integer.class);
        int assuntosAntes = jdbc.queryForObject("SELECT count(*) FROM assunto", Integer.class);
        mockMvc.perform(put("/api/editais/analises/{id}/partes/{p}/nos", id, p3).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nos\": [273], \"natureza\": \"EXATO\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.partes[0].nos[0].nome").value("Arquitetura de Banco de Dados"));
        assertEquals(segmentos, jdbc.queryForObject("SELECT count(*) FROM segmento", Integer.class), "trocar nó não mexe em segmento (D-93)");
        assertEquals(sessoes, jdbc.queryForObject("SELECT count(*) FROM sessao", Integer.class));
        assertEquals(assuntosAntes, jdbc.queryForObject("SELECT count(*) FROM assunto", Integer.class));
    }
}
