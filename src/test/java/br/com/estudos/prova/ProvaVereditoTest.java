package br.com.estudos.prova;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import br.com.estudos.prova.ProvaInterna.ItemVeredito;
import br.com.estudos.prova.ProvaInterna.VereditosRequest;
import br.com.estudos.shared.enums.StatusProva;
import br.com.estudos.shared.enums.VereditoCobertura;

/**
 * Os vereditos entregues pelo worker (docs/SPRINT-24-PROVA-GABARITO.md §3, §10): D-68, D-73.
 * A página citada precisa existir num segmento ativo do assunto da questão.
 */
class ProvaVereditoTest extends ProvaTestBase {

    private org.springframework.test.web.servlet.ResultActions enviar(Long provaId, ItemVeredito... itens) throws Exception {
        return mockMvc.perform(comToken(post("/internal/provas/{id}/vereditos", provaId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo(new VereditosRequest("modelo-teste", List.of(itens)))));
    }

    private static ItemVeredito respondivel(int numero, Long segmentoId, Integer pagina) {
        return new ItemVeredito(numero, VereditoCobertura.RESPONDIVEL, segmentoId, pagina, null);
    }

    @Test
    void d68_citacaoValida_grava_eAProvaFicaPronta() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1)), questao(2, alvo.chave(1))), "A", "B");
        var segmento = segmento(assuntoId(alvo.chave(1)), 1, 1, 10);

        enviar(prova.getId(),
                respondivel(1, segmento, 3),
                new ItemVeredito(2, VereditoCobertura.PARCIAL, segmento, 5, "falta a exceção"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gravados").value(2))
            .andExpect(jsonPath("$.rebaixados.length()").value(0))
            .andExpect(jsonPath("$.status").value("PRONTA"));

        var q1 = questaoRepository.findByProvaIdAndNumero(prova.getId(), 1).orElseThrow();
        var v1 = vereditoRepository.findFirstByQuestaoIdOrderByIdDesc(q1.getId()).orElseThrow();
        assertEquals(VereditoCobertura.RESPONDIVEL, v1.getVeredito());
        assertEquals(3, v1.getPaginaSegmento());
        assertEquals("A", v1.getGabaritoConsiderado(), "guarda a resposta usada, para D-84");
        assertEquals(StatusProva.PRONTA, provaRepository.findById(prova.getId()).orElseThrow().getStatus());
    }

    @Test
    void d68_citacaoInexistente_rebaixa() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1))), "A");

        enviar(prova.getId(), respondivel(1, 999999999L, 3))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gravados").value(1))
            .andExpect(jsonPath("$.rebaixados.length()").value(1))
            .andExpect(jsonPath("$.rebaixados[0].numero").value(1));

        var q = questaoRepository.findByProvaIdAndNumero(prova.getId(), 1).orElseThrow();
        var v = vereditoRepository.findFirstByQuestaoIdOrderByIdDesc(q.getId()).orElseThrow();
        assertEquals(VereditoCobertura.NAO_RESPONDIVEL, v.getVeredito(), "citação que não passa rebaixa (D-68)");
        assertNull(v.getSegmento());
        org.junit.jupiter.api.Assertions.assertTrue(v.getFalta().contains("D-68"));
    }

    @Test
    void d68_segmentoArquivado_naoSustenta() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1))), "A");
        var segmento = segmento(assuntoId(alvo.chave(1)), 1, 1, 10);
        jdbc.update("update segmento set arquivado_em = now() where id = ?", segmento);

        enviar(prova.getId(), respondivel(1, segmento, 3)).andExpect(jsonPath("$.rebaixados.length()").value(1));
    }

    @Test
    void d68_segmentoDeOutroAssunto_naoSustenta() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1))), "A");
        var doOutro = segmento(assuntoId(alvo.chave(2)), 1, 1, 10);

        enviar(prova.getId(), respondivel(1, doOutro, 3)).andExpect(jsonPath("$.rebaixados.length()").value(1));
    }

    @Test
    void d68_paginaForaDoSegmento_naoSustenta() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1))), "A");
        var segmento = segmento(assuntoId(alvo.chave(1)), 1, 1, 10);

        enviar(prova.getId(), respondivel(1, segmento, 11)).andExpect(jsonPath("$.rebaixados.length()").value(1));
        // O primeiro lote fechou a prova; o fluxo real reabre por "Analisar de novo" (24.3).
        jdbc.update("update prova set status = 'JULGANDO' where id = ?", prova.getId());
        enviar(prova.getId(), respondivel(1, segmento, 10)).andExpect(jsonPath("$.rebaixados.length()").value(0));
    }

    @Test
    void questaoNaoAvaliavelOuInexistente_voltaEmRecusados() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1)), questao(2, null)), "A", "B");
        var segmento = segmento(assuntoId(alvo.chave(1)), 1, 1, 10);

        enviar(prova.getId(), respondivel(2, segmento, 3), respondivel(99, segmento, 3))
            .andExpect(jsonPath("$.gravados").value(0))
            .andExpect(jsonPath("$.recusados.length()").value(2));
    }

    @Test
    void foraDoEstadoJulgando_409() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, "gabarito.pdf");
        provaService.entregarLeitura(prova.getId(), leitura(List.of(questao(1, alvo.chave(1))),
            gabarito(br.com.estudos.shared.enums.SituacaoGabarito.PRELIMINAR, "A")));

        enviar(prova.getId(), respondivel(1, 1L, 1))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
    }
}
