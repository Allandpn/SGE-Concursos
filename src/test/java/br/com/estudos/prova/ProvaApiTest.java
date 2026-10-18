package br.com.estudos.prova;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.estudos.prova.ProvaInterna.AlternativaLida;
import br.com.estudos.prova.ProvaInterna.ItemVeredito;
import br.com.estudos.prova.ProvaInterna.QuestaoLida;
import br.com.estudos.prova.ProvaInterna.RespostaGabarito;
import br.com.estudos.prova.ProvaInterna.VereditosRequest;
import br.com.estudos.shared.enums.SituacaoGabarito;
import br.com.estudos.shared.enums.StatusProva;
import br.com.estudos.shared.enums.VereditoCobertura;

/**
 * J-6 pelo lado do usuário (docs/SPRINT-24-PROVA-GABARITO.md §4, §6, §10): envio, conferência,
 * correção, confirmação, reanálise e relatório. D-70, D-71, D-72, D-74, D-79, D-81.
 */
class ProvaApiTest extends ProvaTestBase {

    private static byte[] pdf() {
        return ("%PDF-1.4\n% prova de teste " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
    }

    private static MockMultipartFile arquivoProva(byte[] bytes) {
        return new MockMultipartFile("prova", "prova-fcc.pdf", "application/pdf", bytes);
    }

    private ResultActions enviar(MockMultipartFile prova, MockMultipartFile gabarito, String gabaritoTexto, String tipo) throws Exception {
        var pedido = multipart("/api/provas").file(prova);
        if (gabarito != null) pedido.file(gabarito);
        if (gabaritoTexto != null) pedido.param("gabaritoTexto", gabaritoTexto);
        if (tipo != null) pedido.param("tipo", tipo);
        return mockMvc.perform(pedido);
    }

    private static List<QuestaoLida> oitoQuestoes(Alvo alvo) {
        return List.of(
            questao(1, alvo.chave(1)), questao(2, alvo.chave(1)), questao(3, alvo.chave(1)), questao(4, alvo.chave(1)),
            new QuestaoLida(5, "Enunciado 5", null, null, List.of(new AlternativaLida("A", "a"), new AlternativaLida("B", "b")),
                alvo.chave(2), true, false, null, null),
            questao(6, null),
            new QuestaoLida(7, null, null, null, null, null, false, true, 3, null),
            questao(8, alvo.chave(1)));
    }

    private static ProvaInterna.GabaritoLido gabaritoDeOito() {
        return new ProvaInterna.GabaritoLido(SituacaoGabarito.PRELIMINAR, "cargo E05, tipo 4", List.of(
            new RespostaGabarito(1, "A", null), new RespostaGabarito(2, "B", null), new RespostaGabarito(3, "C", null),
            new RespostaGabarito(4, "D", null), new RespostaGabarito(5, "E", null), new RespostaGabarito(6, "A", null),
            new RespostaGabarito(7, "B", null), new RespostaGabarito(8, "*", null)));
    }

    // ------------------------------------------------------------ envio

    @Test
    void envio_criaProvaEmLendo_eGravaOArquivoNoVolume() throws Exception {
        alvo();
        var bytes = pdf();

        var resposta = enviar(arquivoProva(bytes), null, null, "4")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("LENDO"))
            .andExpect(jsonPath("$.nome").value("prova-fcc.pdf"))
            .andExpect(jsonPath("$.tipo").value("4"))
            .andExpect(jsonPath("$.temGabarito").value(false))
            .andReturn().getResponse().getContentAsString();

        int id = JsonPath.read(resposta, "$.id");
        assertArrayEquals(bytes, Files.readAllBytes(PROVAS.resolve("entrada").resolve(String.valueOf(id)).resolve("prova.pdf")));
    }

    @Test
    void envio_comGabaritoEmTexto_gravaOTxt_eOWorkerOEnxerga() throws Exception {
        alvo();

        var resposta = enviar(arquivoProva(pdf()), null, "1 A\n2 B\n3 *", null)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.temGabarito").value(true))
            .andReturn().getResponse().getContentAsString();

        int id = JsonPath.read(resposta, "$.id");
        assertEquals("1 A\n2 B\n3 *", Files.readString(PROVAS.resolve("entrada").resolve(String.valueOf(id)).resolve("gabarito.txt")));
        mockMvc.perform(comToken(get("/internal/provas/pendentes")))
            .andExpect(jsonPath("$[?(@.id == " + id + ")].gabaritoArquivo").value("gabarito.txt"));
    }

    @Test
    void envio_comGabaritoEmPdf_gravaOPdf() throws Exception {
        alvo();
        var gabarito = pdf();

        var resposta = enviar(arquivoProva(pdf()), new MockMultipartFile("gabarito", "g.pdf", "application/pdf", gabarito), null, null)
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        int id = JsonPath.read(resposta, "$.id");
        assertArrayEquals(gabarito, Files.readAllBytes(PROVAS.resolve("entrada").resolve(String.valueOf(id)).resolve("gabarito.pdf")));
    }

    @Test
    void envio_mesmaProvaDeNovo_devolveAExistente() throws Exception {
        alvo();
        var bytes = pdf();
        var primeira = enviar(arquivoProva(bytes), null, null, null)
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        enviar(arquivoProva(bytes), null, null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value((int) JsonPath.read(primeira, "$.id")));
    }

    @Test
    void envio_recusaQuemNaoEPdf_eGabaritoDuplicado() throws Exception {
        alvo();

        enviar(arquivoProva("isto não é um pdf".getBytes(StandardCharsets.UTF_8)), null, null, null)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PROVA_NAO_E_PDF"));
        enviar(arquivoProva(pdf()), new MockMultipartFile("gabarito", "g.pdf", "application/pdf", "texto".getBytes(StandardCharsets.UTF_8)), null, null)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("GABARITO_NAO_E_PDF"));
        enviar(arquivoProva(pdf()), new MockMultipartFile("gabarito", "g.pdf", "application/pdf", pdf()), "1 A", null)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("GABARITO_DUPLICADO"));
    }

    @Test
    void envio_semConcursoAlvo_422() throws Exception {
        jdbc.update("update concurso set ativo = false where ativo");

        enviar(arquivoProva(pdf()), null, null, null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("SEM_CONCURSO_ALVO"));
    }

    @Test
    void anexarGabarito_soEmAguardandoGabarito_eVoltaALendo() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, null);
        provaService.entregarLeitura(prova.getId(), leitura(List.of(questao(1, alvo.chave(1))), null));

        mockMvc.perform(multipart("/api/provas/{id}/gabarito", prova.getId()).param("gabaritoTexto", "1 A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LENDO"));
        assertEquals("1 A", Files.readString(PROVAS.resolve("entrada").resolve(prova.getId().toString()).resolve("gabarito.txt")));
        mockMvc.perform(comToken(get("/internal/provas/pendentes")))
            .andExpect(jsonPath("$[?(@.id == " + prova.getId() + ")].jaLida").value(true));

        mockMvc.perform(multipart("/api/provas/{id}/gabarito", prova.getId()).param("gabaritoTexto", "1 B"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
    }

    // ------------------------------------------------------------ conferência

    @Test
    void conferencia_mostraOQueFoiLido_amostraEspalhada_eAsDuvidosas() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        provaService.entregarLeitura(prova.getId(), leitura(oitoQuestoes(alvo), gabaritoDeOito()));

        mockMvc.perform(get("/api/provas/{id}", prova.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resumo.status").value("CONFERIR"))
            .andExpect(jsonPath("$.resumo.questoes").value(8))
            .andExpect(jsonPath("$.avaliaveis").value(5))
            .andExpect(jsonPath("$.gabaritoCasado").value(8))
            .andExpect(jsonPath("$.gabaritoRecorte").value("cargo E05, tipo 4"))
            .andExpect(jsonPath("$.naoAvaliaveis.ANULADA").value(1))
            .andExpect(jsonPath("$.naoAvaliaveis.SEM_TEXTO").value(1))
            .andExpect(jsonPath("$.foraDoEdital").value(1))
            // Avaliáveis 1 a 5: posições ¼, ½ e ¾ são as questões 2, 3 e 4.
            .andExpect(jsonPath("$.amostra.length()").value(3))
            .andExpect(jsonPath("$.amostra[0].numero").value(2))
            .andExpect(jsonPath("$.amostra[1].numero").value(3))
            .andExpect(jsonPath("$.amostra[2].numero").value(4))
            .andExpect(jsonPath("$.amostra[0].respostaGabarito").value("B"))
            .andExpect(jsonPath("$.amostra[0].alternativaGabarito").value("b"))
            .andExpect(jsonPath("$.duvidosas.length()").value(1))
            .andExpect(jsonPath("$.duvidosas[0].numero").value(5))
            .andExpect(jsonPath("$.duvidosas[0].atribuicao").value("REVISAR"));
    }

    @Test
    void lista_soAsProvasDoConcursoAlvo() throws Exception {
        var antigo = alvo();
        var daAntiga = prova(antigo, null);
        var atual = alvo();
        var daAtual = prova(atual, null);

        var corpo = mockMvc.perform(get("/api/provas")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(corpo, "$[*].id");
        assertTrue(ids.contains(daAtual.getId().intValue()));
        assertTrue(!ids.contains(daAntiga.getId().intValue()), "a prova do concurso anterior não aparece (D-77)");
    }

    // ------------------------------------------------------------ correção e confirmação

    @Test
    void corrigir_nomeBancaAno_eTipoSoAteOGabaritoValer() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        provaService.entregarLeitura(prova.getId(), leitura(oitoQuestoes(alvo), gabaritoDeOito()));

        mockMvc.perform(patch("/api/provas/{id}", prova.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \"TJ-CE 2026\", \"banca\": \"FCC\", \"ano\": 2026, \"tipo\": \"4\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("TJ-CE 2026"))
            .andExpect(jsonPath("$.banca").value("FCC"));
        mockMvc.perform(patch("/api/provas/{id}", prova.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"nome\": \"  \"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("NOME_OBRIGATORIO"));
        mockMvc.perform(patch("/api/provas/{id}", prova.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"ano\": 1800}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("ANO_INVALIDO"));

        jdbc.update("update prova set status = 'JULGANDO' where id = ?", prova.getId());
        mockMvc.perform(patch("/api/provas/{id}", prova.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"tipo\": \"3\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        mockMvc.perform(patch("/api/provas/{id}", prova.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"banca\": \"FGV\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void d74_corrigirAssunto_daCobertura_ou_foraDoEdital_soNaConferencia() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        provaService.entregarLeitura(prova.getId(), leitura(oitoQuestoes(alvo), gabaritoDeOito()));
        var terceiro = assuntoId(alvo.chave(3));

        mockMvc.perform(put("/api/provas/{id}/questoes/{n}/assunto", prova.getId(), 5).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assuntoId\": " + terceiro + "}"))
            .andExpect(status().isNoContent());
        var q5 = questaoRepository.findByProvaIdAndNumero(prova.getId(), 5).orElseThrow();
        assertEquals(alvo.chave(3), chaveDoAssuntoDaQuestao(q5.getId()));
        assertEquals("CONFIRMADA", q5.getAtribuicao().name());

        mockMvc.perform(put("/api/provas/{id}/questoes/{n}/assunto", prova.getId(), 1).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assuntoId\": null}"))
            .andExpect(status().isNoContent());
        assertEquals("FORA_DO_EDITAL", questaoRepository.findByProvaIdAndNumero(prova.getId(), 1).orElseThrow().getAtribuicao().name());

        mockMvc.perform(put("/api/provas/{id}/questoes/{n}/assunto", prova.getId(), 2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assuntoId\": 999999999}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("ASSUNTO_FORA_DA_COBERTURA"));
        mockMvc.perform(put("/api/provas/{id}/questoes/{n}/assunto", prova.getId(), 99).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assuntoId\": null}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("QUESTAO_INEXISTENTE"));

        jdbc.update("update prova set status = 'JULGANDO' where id = ?", prova.getId());
        mockMvc.perform(put("/api/provas/{id}/questoes/{n}/assunto", prova.getId(), 2).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assuntoId\": " + terceiro + "}"))
            .andExpect(status().isConflict());
    }

    @Test
    void confirmar_levaAJulgando_ESemQuestaoAvaliavel_422() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        provaService.entregarLeitura(prova.getId(), leitura(oitoQuestoes(alvo), gabaritoDeOito()));

        mockMvc.perform(post("/api/provas/{id}/confirmar", prova.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("JULGANDO"));
        mockMvc.perform(post("/api/provas/{id}/confirmar", prova.getId()))
            .andExpect(status().isConflict());

        var vazia = prova(alvo, "gabarito.pdf");
        provaService.entregarLeitura(vazia.getId(), leitura(List.of(questao(1, null), questao(2, null)),
            gabarito(SituacaoGabarito.PRELIMINAR, "A", "B")));
        mockMvc.perform(post("/api/provas/{id}/confirmar", vazia.getId()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("SEM_QUESTAO_AVALIAVEL"));
    }

    // ------------------------------------------------------------ reanálise e nova tentativa

    private Prova provaPronta(Alvo alvo, Long segmentoDoAssunto1) {
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1)), questao(2, alvo.chave(1))), "A", "B");
        jdbc.update("update segmento set criado_em = now() - interval '1 hour' where id = ?", segmentoDoAssunto1);
        provaService.registrarVereditos(prova.getId(), new VereditosRequest("m", List.of(
            new ItemVeredito(1, VereditoCobertura.RESPONDIVEL, segmentoDoAssunto1, 2, null),
            new ItemVeredito(2, VereditoCobertura.PARCIAL, segmentoDoAssunto1, 3, "falta a exceção"))));
        return provaRepository.findById(prova.getId()).orElseThrow();
    }

    @Test
    void d79_analisarDeNovo_semMudancaFicaPronta_comMaterialNovoReabre() throws Exception {
        var alvo = alvo();
        var s1 = segmento(assuntoId(alvo.chave(1)), 1, 1, 10);
        var prova = provaPronta(alvo, s1);
        assertEquals(StatusProva.PRONTA, prova.getStatus());

        mockMvc.perform(post("/api/provas/{id}/analisar-de-novo", prova.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PRONTA"))
            .andExpect(jsonPath("$.aJulgar").value(0));

        var novo = segmento(assuntoId(alvo.chave(1)), 2, 11, 20);
        jdbc.update("update segmento set criado_em = now() + interval '1 hour' where id = ?", novo);

        mockMvc.perform(post("/api/provas/{id}/analisar-de-novo", prova.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("JULGANDO"))
            .andExpect(jsonPath("$.aJulgar").value(2));
        mockMvc.perform(post("/api/provas/{id}/analisar-de-novo", prova.getId()))
            .andExpect(status().isConflict());
    }

    @Test
    void tentarDeNovo_voltaParaAEtapaQueFalhou() throws Exception {
        var prova = prova(alvo(), null);
        mockMvc.perform(post("/api/provas/{id}/tentar-de-novo", prova.getId())).andExpect(status().isConflict());

        provaService.falhar(prova.getId(), new ProvaInterna.FalhaRequest(StatusProva.LENDO, "OCR sem texto"));
        mockMvc.perform(post("/api/provas/{id}/tentar-de-novo", prova.getId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("LENDO"));
        var salva = provaRepository.findById(prova.getId()).orElseThrow();
        assertEquals(null, salva.getErro());

        jdbc.update("update prova set status = 'FALHOU', etapa_falha = 'JULGANDO', erro = 'x', formato = 'MULTIPLA_ESCOLHA', gabarito_situacao = 'PRELIMINAR' where id = ?", prova.getId());
        mockMvc.perform(post("/api/provas/{id}/tentar-de-novo", prova.getId()))
            .andExpect(jsonPath("$.status").value("JULGANDO"));
    }

    // ------------------------------------------------------------ relatório

    @Test
    void relatorio_denominadorSempre_foraDoEditalNaoEFuro_eSemEnunciado() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(
                questao(1, alvo.chave(1)), questao(2, alvo.chave(1)), questao(3, alvo.chave(2)),
                questao(4, null), questao(5, alvo.chave(1))),
            "A", "B", "C", "D", "*");
        var s1 = segmento(assuntoId(alvo.chave(1)), 1, 1, 10);
        var s2 = segmento(assuntoId(alvo.chave(2)), 1, 1, 10);
        provaService.registrarVereditos(prova.getId(), new VereditosRequest("m", List.of(
            new ItemVeredito(1, VereditoCobertura.RESPONDIVEL, s1, 2, null),
            new ItemVeredito(2, VereditoCobertura.PARCIAL, s1, 3, "falta a exceção"),
            new ItemVeredito(3, VereditoCobertura.NAO_RESPONDIVEL, null, null, "o material não traz o conceito"))));

        mockMvc.perform(get("/api/provas/{id}/relatorio", prova.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PRONTA"))
            .andExpect(jsonPath("$.gabaritoPreliminar").value(true))
            // Os dois números (D-71): 3 avaliáveis, das quais 1 respondível.
            .andExpect(jsonPath("$.cobertura.avaliaveis").value(3))
            .andExpect(jsonPath("$.cobertura.respondiveis").value(1))
            .andExpect(jsonPath("$.cobertura.parciais").value(1))
            .andExpect(jsonPath("$.cobertura.naoRespondiveis").value(1))
            .andExpect(jsonPath("$.cobertura.semVeredito").value(0))
            // O que sai do denominador vem contado à parte (D-70), e fora do edital não é furo (D-74).
            .andExpect(jsonPath("$.declaradas.foraDoEdital").value(1))
            .andExpect(jsonPath("$.declaradas.anuladas").value(1))
            .andExpect(jsonPath("$.porDisciplina.length()").value(1))
            .andExpect(jsonPath("$.porDisciplina[0].cobertura.avaliaveis").value(3))
            .andExpect(jsonPath("$.assuntosFurados.length()").value(2))
            .andExpect(jsonPath("$.assuntosFurados[0].parciais").value(1))
            .andExpect(jsonPath("$.assuntosFurados[0].faltas[0]").value("falta a exceção"))
            .andExpect(jsonPath("$.assuntosFurados[1].naoRespondiveis").value(1))
            .andExpect(jsonPath("$.assuntosSemQuestao.length()").value(1))
            .andExpect(jsonPath("$.questoes.length()").value(5))
            .andExpect(jsonPath("$.questoes[0].segmentoId").value(s1))
            .andExpect(jsonPath("$.questoes[0].paginaSegmento").value(2))
            // D-72: o relatório nunca traz o enunciado.
            .andExpect(content().string(not(containsString("Enunciado 1"))));
    }

    @Test
    void relatorio_antesDoJulgamento_mostraSemVeredito() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1)), questao(2, alvo.chave(2))), "A", "B");

        mockMvc.perform(get("/api/provas/{id}/relatorio", prova.getId()))
            .andExpect(jsonPath("$.cobertura.avaliaveis").value(2))
            .andExpect(jsonPath("$.cobertura.semVeredito").value(2))
            .andExpect(jsonPath("$.cobertura.respondiveis").value(0));
    }

    @Test
    void relatorio_deProvaAindaNaoLida_409() throws Exception {
        var prova = prova(alvo(), null);

        mockMvc.perform(get("/api/provas/{id}/relatorio", prova.getId())).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------ páginas (D-85)

    @Test
    void d85_pagina_servidaDoDiretorioDaProva_enuncaDeFora() throws Exception {
        var prova = prova(alvo(), null);
        var pasta = PROVAS.resolve("paginas").resolve(prova.getId().toString());
        Files.createDirectories(pasta);
        var imagem = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01};
        Files.write(pasta.resolve("01.jpg"), imagem);

        var corpo = mockMvc.perform(get("/api/provas/{id}/paginas/{n}", prova.getId(), 1))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(imagem, corpo);
        mockMvc.perform(get("/api/provas/{id}/paginas/{n}", prova.getId(), 2))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("PAGINA_INEXISTENTE"));
        mockMvc.perform(get("/api/provas/{id}/paginas/{n}", prova.getId(), 0)).andExpect(status().isNotFound());
        // Uma prova não enxerga a pasta de outra.
        var outra = prova(alvo(), null);
        mockMvc.perform(get("/api/provas/{id}/paginas/{n}", outra.getId(), 1)).andExpect(status().isNotFound());
    }
}
