package br.com.estudos.prova;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import br.com.estudos.prova.ProvaInterna.AlternativaLida;
import br.com.estudos.prova.ProvaInterna.GabaritoRequest;
import br.com.estudos.prova.ProvaInterna.QuestaoLida;
import br.com.estudos.prova.ProvaInterna.RespostaGabarito;
import br.com.estudos.shared.enums.AtribuicaoQuestao;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.MotivoNaoAvaliavel;
import br.com.estudos.shared.enums.SituacaoGabarito;
import br.com.estudos.shared.enums.StatusProva;

/**
 * A leitura do planejamento entregue ao SGE (docs/SPRINT-24-PROVA-GABARITO.md §3, §10):
 * D-70, D-74, D-81, D-82.
 */
class ProvaLeituraTest extends ProvaTestBase {

    private ProvaInterna.LeituraRequest leituraTresQuestoes(Alvo alvo, ProvaInterna.GabaritoLido gabarito) {
        return leitura(List.of(questao(1, alvo.chave(1)), questao(2, alvo.chave(1)), questao(3, alvo.chave(2))), gabarito);
    }

    @Test
    void semToken_recusa() throws Exception {
        mockMvc.perform(get("/internal/provas/pendentes")).andExpect(status().isUnauthorized());
    }

    @Test
    void pendentes_listaALerComOQueFalta() throws Exception {
        var prova = prova(alvo(), "gabarito.pdf");

        mockMvc.perform(comToken(get("/internal/provas/pendentes")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + prova.getId() + ")].status").value("LENDO"))
            .andExpect(jsonPath("$[?(@.id == " + prova.getId() + ")].jaLida").value(false))
            .andExpect(jsonPath("$[?(@.id == " + prova.getId() + ")].gabaritoArquivo").value("gabarito.pdf"));
    }

    @Test
    void dadosDeLeitura_trazOsAssuntosDaCoberturaDoAlvo() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, null);

        mockMvc.perform(comToken(get("/internal/provas/{id}/leitura", prova.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.arquivoProva").value("entrada/" + prova.getId() + "/prova.pdf"))
            .andExpect(jsonPath("$.assuntos.length()").value(3))
            .andExpect(jsonPath("$.assuntos[0].chaveExterna").exists());
    }

    @Test
    void leituraComGabarito_vaiParaConferir() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        var pedido = leituraTresQuestoes(alvo, gabarito(SituacaoGabarito.PRELIMINAR, "A", "B", "C"));

        mockMvc.perform(comToken(post("/internal/provas/{id}/leitura", prova.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(pedido)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFERIR"));

        var salva = provaRepository.findById(prova.getId()).orElseThrow();
        assertEquals(StatusProva.CONFERIR, salva.getStatus());
        assertEquals(SituacaoGabarito.PRELIMINAR, salva.getGabaritoSituacao());
        assertEquals(FormatoBanca.MULTIPLA_ESCOLHA, salva.getFormato());
        assertEquals(3, salva.getQuestoesEsperadas());
        var q1 = questaoRepository.findByProvaIdAndNumero(prova.getId(), 1).orElseThrow();
        assertEquals("A", q1.getRespostaGabarito());
        assertEquals(AtribuicaoQuestao.AUTOMATICA, q1.getAtribuicao());
        assertEquals(alvo.chave(1), chaveDoAssuntoDaQuestao(q1.getId()));
        assertNull(q1.getMotivoNaoAvaliavel());
    }

    @Test
    void d81_leituraSemGabarito_esperaEAsQuestoesSaemDoDenominador() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, null);

        provaService.entregarLeitura(prova.getId(), leituraTresQuestoes(alvo, null));

        var salva = provaRepository.findById(prova.getId()).orElseThrow();
        assertEquals(StatusProva.AGUARDANDO_GABARITO, salva.getStatus());
        assertNull(salva.getGabaritoSituacao(), "sem gabarito, nada é julgado (D-81)");
        for (var q : questaoRepository.findByProvaIdOrderByNumeroAsc(prova.getId())) {
            assertEquals(MotivoNaoAvaliavel.SEM_GABARITO, q.getMotivoNaoAvaliavel());
        }
    }

    @Test
    void gabaritoDepois_levaAConferirEFazAsQuestoesAvaliaveis() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, null);
        provaService.entregarLeitura(prova.getId(), leituraTresQuestoes(alvo, null));
        var pedido = new GabaritoRequest("modelo", gabarito(SituacaoGabarito.DEFINITIVO, "E", "D", "C"));

        mockMvc.perform(comToken(post("/internal/provas/{id}/gabarito", prova.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(pedido)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFERIR"));

        assertEquals(SituacaoGabarito.DEFINITIVO, provaRepository.findById(prova.getId()).orElseThrow().getGabaritoSituacao());
        var q2 = questaoRepository.findByProvaIdAndNumero(prova.getId(), 2).orElseThrow();
        assertEquals("D", q2.getRespostaGabarito());
        assertNull(q2.getMotivoNaoAvaliavel());
    }

    @Test
    void d82_gabaritoComNumeroDiferente_recusaETudoFicaComoEstava() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        var pedido = leituraTresQuestoes(alvo, gabarito(SituacaoGabarito.PRELIMINAR, "A", "B"));

        mockMvc.perform(comToken(post("/internal/provas/{id}/leitura", prova.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(pedido)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("GABARITO_NAO_CASA"));

        assertEquals(StatusProva.LENDO, provaRepository.findById(prova.getId()).orElseThrow().getStatus());
        assertEquals(0, questaoRepository.findByProvaIdOrderByNumeroAsc(prova.getId()).size(), "tudo ou nada");
    }

    @Test
    void d74_assuntoForaDaCobertura_ouNenhum_viraForaDoEdital() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        var pedido = leitura(List.of(
                questao(1, "nao-existe-na-cobertura"),
                questao(2, null),
                semTexto(3)),
            gabarito(SituacaoGabarito.PRELIMINAR, "A", "B", "C"));

        provaService.entregarLeitura(prova.getId(), pedido);

        var q1 = questaoRepository.findByProvaIdAndNumero(prova.getId(), 1).orElseThrow();
        assertEquals(AtribuicaoQuestao.FORA_DO_EDITAL, q1.getAtribuicao());
        assertNull(chaveDoAssuntoDaQuestao(q1.getId()));
        assertEquals(AtribuicaoQuestao.FORA_DO_EDITAL, questaoRepository.findByProvaIdAndNumero(prova.getId(), 2).orElseThrow().getAtribuicao());
        var q3 = questaoRepository.findByProvaIdAndNumero(prova.getId(), 3).orElseThrow();
        assertEquals(MotivoNaoAvaliavel.SEM_TEXTO, q3.getMotivoNaoAvaliavel(), "sem enunciado não há o que julgar (D-70)");
        assertEquals(AtribuicaoQuestao.PENDENTE, q3.getAtribuicao(), "ilegível não é fora do edital");
    }

    @Test
    void baixaConfianca_vaiParaRevisao() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        var duvidosa = new QuestaoLida(1, "Enunciado 1", null, null, List.of(new AlternativaLida("A", "a")),
            alvo.chave(3), true, false, null, null);

        provaService.entregarLeitura(prova.getId(), leitura(List.of(duvidosa), gabarito(SituacaoGabarito.PRELIMINAR, "A")));

        var q = questaoRepository.findByProvaIdAndNumero(prova.getId(), 1).orElseThrow();
        assertEquals(AtribuicaoQuestao.REVISAR, q.getAtribuicao());
        assertEquals(alvo.chave(3), chaveDoAssuntoDaQuestao(q.getId()), "a sugestão fica para o usuário confirmar");
    }

    @Test
    void d70_anuladaSaiDoDenominador_semResposta() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        var respostas = new ArrayList<>(List.of(
            new RespostaGabarito(1, "A", null), new RespostaGabarito(2, "*", null), new RespostaGabarito(3, null, true)));
        var gabarito = new ProvaInterna.GabaritoLido(SituacaoGabarito.DEFINITIVO, "recorte", respostas);

        provaService.entregarLeitura(prova.getId(), leituraTresQuestoes(alvo, gabarito));

        assertNull(questaoRepository.findByProvaIdAndNumero(prova.getId(), 1).orElseThrow().getMotivoNaoAvaliavel());
        for (var numero : List.of(2, 3)) {
            var q = questaoRepository.findByProvaIdAndNumero(prova.getId(), numero).orElseThrow();
            assertEquals(MotivoNaoAvaliavel.ANULADA, q.getMotivoNaoAvaliavel());
            assertNull(q.getRespostaGabarito());
        }
    }

    @Test
    void certoErrado_soAceitaCeE() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        var itens = List.of(new QuestaoLida(1, "O item afirma X.", "Texto-base do item", null, null,
            alvo.chave(1), false, false, null, null));
        var invalido = new ProvaInterna.LeituraRequest("Prova CE", "CEBRASPE", (short) 2026, null, null,
            FormatoBanca.CERTO_ERRADO, "m", itens, gabarito(SituacaoGabarito.DEFINITIVO, "A"), null);

        mockMvc.perform(comToken(post("/internal/provas/{id}/leitura", prova.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(invalido)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("LETRA_INVALIDA"));

        var valido = new ProvaInterna.LeituraRequest("Prova CE", "CEBRASPE", (short) 2026, null, null,
            FormatoBanca.CERTO_ERRADO, "m", itens, gabarito(SituacaoGabarito.DEFINITIVO, "C"), null);
        provaService.entregarLeitura(prova.getId(), valido);
        var q = questaoRepository.findByProvaIdAndNumero(prova.getId(), 1).orElseThrow();
        assertEquals("C", q.getRespostaGabarito());
        assertEquals("Texto-base do item", q.getTextoBase());
        assertEquals(0L, jdbc.queryForObject("select count(*) from alternativa where questao_id = ?", Long.class, q.getId()),
            "certo/errado não tem alternativas");
    }

    @Test
    void gabaritoRecusadoNaSegundaLeitura_voltaAEsperarComOMotivo_semGabaritoInventado() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, null);
        provaService.entregarLeitura(prova.getId(), leituraTresQuestoes(alvo, null));
        // O usuário enviou o gabarito: a prova volta a LENDO, e o worker lê só o gabarito.
        jdbc.update("update prova set status = 'LENDO', gabarito_arquivo = 'gabarito.pdf' where id = ?", prova.getId());

        mockMvc.perform(comToken(post("/internal/provas/{id}/gabarito", prova.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modelo\": \"m\", \"gabarito\": null, \"avisoGabarito\": \"O tipo 4 não está no arquivo.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("AGUARDANDO_GABARITO"));

        var esperando = provaRepository.findById(prova.getId()).orElseThrow();
        assertNull(esperando.getGabaritoSituacao(), "recusar o gabarito não inventa gabarito (D-81)");
        assertNull(esperando.getGabaritoArquivo(), "o arquivo que não serviu sai, para o usuário enviar outro");
        assertEquals("O tipo 4 não está no arquivo.", esperando.getErro());
        mockMvc.perform(comToken(post("/internal/provas/{id}/gabarito", prova.getId()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"modelo\": \"m\", \"gabarito\": null}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("GABARITO_OBRIGATORIO"));
    }

    @Test
    void avisoDoGabarito_ficaNaProvaQueEspera_eSomeQuandoOGabaritoChega() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        var semGabarito = new ProvaInterna.LeituraRequest("Prova", "FGV", (short) 2026, "1", "Cargo X",
            FormatoBanca.MULTIPLA_ESCOLHA, "m", List.of(questao(1, alvo.chave(1))), null,
            "O gabarito traz 59 respostas e a prova 60 questões.");

        provaService.entregarLeitura(prova.getId(), semGabarito);

        var esperando = provaRepository.findById(prova.getId()).orElseThrow();
        assertEquals(StatusProva.AGUARDANDO_GABARITO, esperando.getStatus());
        assertEquals("O gabarito traz 59 respostas e a prova 60 questões.", esperando.getErro(), "o usuário vê o motivo, não um silêncio");
        mockMvc.perform(get("/api/provas/{id}", prova.getId()))
            .andExpect(jsonPath("$.erro").value("O gabarito traz 59 respostas e a prova 60 questões."));

        provaService.entregarGabarito(prova.getId(), new GabaritoRequest("m", gabarito(SituacaoGabarito.DEFINITIVO, "A")));
        assertNull(provaRepository.findById(prova.getId()).orElseThrow().getErro(), "o aviso some com o gabarito");
    }

    @Test
    void dadosDeLeitura_depoisDeLida_trazFormatoCargoENumerosParaLerSoOGabarito() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, null);
        provaService.entregarLeitura(prova.getId(), leituraTresQuestoes(alvo, null));
        jdbc.update("update prova set status = 'LENDO' where id = ?", prova.getId());

        mockMvc.perform(comToken(get("/internal/provas/{id}/leitura", prova.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jaLida").value(true))
            .andExpect(jsonPath("$.formato").value("MULTIPLA_ESCOLHA"))
            .andExpect(jsonPath("$.cargoProva").value("Cargo X"))
            .andExpect(jsonPath("$.numerosDasQuestoes.length()").value(3))
            .andExpect(jsonPath("$.numerosDasQuestoes[2]").value(3));
    }

    @Test
    void leituraDuasVezes_naoVaiParaOMesmoEstado() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        var pedido = leituraTresQuestoes(alvo, gabarito(SituacaoGabarito.PRELIMINAR, "A", "B", "C"));
        provaService.entregarLeitura(prova.getId(), pedido);

        mockMvc.perform(comToken(post("/internal/provas/{id}/leitura", prova.getId()))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(pedido)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
    }

    @Test
    void falha_marcaAEtapa() throws Exception {
        var prova = prova(alvo(), null);

        mockMvc.perform(comToken(post("/internal/provas/{id}/falha", prova.getId()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(new ProvaInterna.FalhaRequest(StatusProva.LENDO, "OCR não achou texto"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FALHOU"));

        var salva = provaRepository.findById(prova.getId()).orElseThrow();
        assertEquals(StatusProva.LENDO, salva.getEtapaFalha());
        assertEquals("OCR não achou texto", salva.getErro());
    }

    @Test
    void provaInexistente_404() throws Exception {
        mockMvc.perform(comToken(get("/internal/provas/{id}/leitura", 999999999)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("PROVA_INEXISTENTE"));
    }

    private static QuestaoLida semTexto(int numero) {
        return new QuestaoLida(numero, null, null, null, null, null, false, true, 2, null);
    }
}
