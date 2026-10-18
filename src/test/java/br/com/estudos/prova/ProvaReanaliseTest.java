package br.com.estudos.prova;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import br.com.estudos.prova.ProvaInterna.GabaritoRequest;
import br.com.estudos.prova.ProvaInterna.GabaritoLido;
import br.com.estudos.prova.ProvaInterna.ItemVeredito;
import br.com.estudos.prova.ProvaInterna.RespostaGabarito;
import br.com.estudos.prova.ProvaInterna.VereditosRequest;
import br.com.estudos.shared.enums.MotivoNaoAvaliavel;
import br.com.estudos.shared.enums.SituacaoGabarito;
import br.com.estudos.shared.enums.StatusProva;
import br.com.estudos.shared.enums.VereditoCobertura;

/**
 * A reanálise incremental, derivada dos segmentos e do gabarito (D-79, D-84;
 * docs/SPRINT-24-PROVA-GABARITO.md §5). Nenhuma coluna de "sujo": a consulta decide.
 * O relógio de referência é o do banco, o mesmo que carimba o veredito.
 */
class ProvaReanaliseTest extends ProvaTestBase {

    private record Cenario(Alvo alvo, Prova prova, Long segmentoDoAssunto1, Long segmentoDoAssunto2) {}

    /** Duas questões no assunto 1, uma no assunto 2, uma fora do edital; as três primeiras já julgadas. */
    private Cenario julgado() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo,
            List.of(questao(1, alvo.chave(1)), questao(2, alvo.chave(1)), questao(3, alvo.chave(2)), questao(4, null)),
            "A", "B", "C", "D");
        var s1 = segmento(assuntoId(alvo.chave(1)), 1, 1, 10);
        var s2 = segmento(assuntoId(alvo.chave(2)), 1, 1, 10);
        // O material nasceu antes do julgamento: recua o carimbo no relógio do banco.
        jdbc.update("update segmento set criado_em = now() - interval '1 hour' where id in (?, ?)", s1, s2);
        provaService.registrarVereditos(prova.getId(), new VereditosRequest("m", List.of(
            new ItemVeredito(1, VereditoCobertura.RESPONDIVEL, s1, 2, null),
            new ItemVeredito(2, VereditoCobertura.RESPONDIVEL, s1, 3, null),
            new ItemVeredito(3, VereditoCobertura.RESPONDIVEL, s2, 4, null))));
        return new Cenario(alvo, provaRepository.findById(prova.getId()).orElseThrow(), s1, s2);
    }

    private List<Integer> numerosAJulgar(Long provaId) {
        var ids = questaoRepository.idsAJulgar(provaId);
        return questaoRepository.findByProvaIdOrderByNumeroAsc(provaId).stream()
            .filter(q -> ids.contains(q.getId())).map(Questao::getNumero).toList();
    }

    @Test
    void semVeredito_ficaAJulgar_foraDoEditalNao() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1)), questao(2, null)), "A", "B");

        assertEquals(List.of(1), numerosAJulgar(prova.getId()), "fora do edital não é julgado (D-74)");
    }

    @Test
    void semMudanca_naoReabre() throws Exception {
        var c = julgado();

        assertEquals(List.of(), numerosAJulgar(c.prova().getId()));
        assertEquals(StatusProva.PRONTA, c.prova().getStatus());
    }

    @Test
    void d79_assuntoComMaterialNovo_reabreSoAsQuestoesDele() throws Exception {
        var c = julgado();
        var novo = segmento(assuntoId(c.alvo().chave(1)), 2, 11, 20);
        jdbc.update("update segmento set criado_em = now() + interval '1 hour' where id = ?", novo);

        assertEquals(List.of(1, 2), numerosAJulgar(c.prova().getId()), "só o assunto 1 mudou");
    }

    @Test
    void d79_segmentoArquivadoDepois_reabre() throws Exception {
        var c = julgado();
        jdbc.update("update segmento set arquivado_em = now() + interval '1 hour' where id = ?", c.segmentoDoAssunto2());

        assertEquals(List.of(3), numerosAJulgar(c.prova().getId()));
    }

    @Test
    void d79_novoVeredito_fechaDeNovo() throws Exception {
        var c = julgado();
        var novo = segmento(assuntoId(c.alvo().chave(2)), 2, 11, 20);
        jdbc.update("update segmento set criado_em = now() + interval '1 hour' where id = ?", novo);
        assertEquals(List.of(3), numerosAJulgar(c.prova().getId()));

        // "Analisar de novo" (24.3) devolve a prova a JULGANDO; julgar cria linha nova (D-73), com a data de agora.
        jdbc.update("update segmento set criado_em = now() - interval '1 minute' where id = ?", novo);
        jdbc.update("update prova set status = 'JULGANDO' where id = ?", c.prova().getId());
        provaService.registrarVereditos(c.prova().getId(), new VereditosRequest("m", List.of(
            new ItemVeredito(3, VereditoCobertura.PARCIAL, novo, 2, "falta o caso X"))));

        assertEquals(List.of(), numerosAJulgar(c.prova().getId()));
        var q3 = questaoRepository.findByProvaIdAndNumero(c.prova().getId(), 3).orElseThrow();
        assertEquals(VereditoCobertura.PARCIAL, vereditoRepository.findFirstByQuestaoIdOrderByIdDesc(q3.getId()).orElseThrow().getVeredito());
        assertEquals(2L, jdbc.queryForObject("select count(*) from veredito_questao where questao_id = ?", Long.class, q3.getId()),
            "o anterior permanece (D-73)");
    }

    @Test
    void d84_gabaritoDefinitivoMudouUmaResposta_devolveSoEssaQuestaoAoJulgamento() throws Exception {
        var c = julgado();
        // De "A, B, C, D" para "E, B, C, D": só a 1 mudou.
        var definitivo = new GabaritoRequest("m", new GabaritoLido(SituacaoGabarito.DEFINITIVO, "recorte", List.of(
            new RespostaGabarito(1, "E", null), new RespostaGabarito(2, "B", null),
            new RespostaGabarito(3, "C", null), new RespostaGabarito(4, "D", null))));

        mockMvc.perform(comToken(post("/internal/provas/{id}/gabarito", c.prova().getId()))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(definitivo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("JULGANDO"));

        assertEquals(List.of(1), numerosAJulgar(c.prova().getId()));
        assertEquals(SituacaoGabarito.DEFINITIVO, provaRepository.findById(c.prova().getId()).orElseThrow().getGabaritoSituacao());
    }

    @Test
    void d84_definitivoQueAnulaUmaQuestao_tiraDoDenominadorENaoReabreAsOutras() throws Exception {
        var c = julgado();
        var definitivo = new GabaritoRequest("m", new GabaritoLido(SituacaoGabarito.DEFINITIVO, "recorte", List.of(
            new RespostaGabarito(1, "A", null), new RespostaGabarito(2, "*", null),
            new RespostaGabarito(3, "C", null), new RespostaGabarito(4, "D", null))));

        provaService.entregarGabarito(c.prova().getId(), definitivo);

        var q2 = questaoRepository.findByProvaIdAndNumero(c.prova().getId(), 2).orElseThrow();
        assertEquals(MotivoNaoAvaliavel.ANULADA, q2.getMotivoNaoAvaliavel());
        assertEquals(List.of(), numerosAJulgar(c.prova().getId()), "anulada não é julgada, e as demais não mudaram");
        assertEquals(StatusProva.PRONTA, provaRepository.findById(c.prova().getId()).orElseThrow().getStatus());
    }

    @Test
    void gabaritoPreliminar_naoSubstituiOutroEmProvaJaConferida() throws Exception {
        var c = julgado();
        var preliminar = new GabaritoRequest("m", gabarito(SituacaoGabarito.PRELIMINAR, "A", "B", "C", "D"));

        mockMvc.perform(comToken(post("/internal/provas/{id}/gabarito", c.prova().getId()))
                .contentType(MediaType.APPLICATION_JSON).content(corpo(preliminar)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
    }

    @Test
    void julgamento_trazAsQuestoesAJulgarComAssuntoESegmentosAtivos() throws Exception {
        var alvo = alvo();
        var prova = provaJulgando(alvo, List.of(questao(1, alvo.chave(1)), questao(2, null)), "C", "B");
        var ativo = segmento(assuntoId(alvo.chave(1)), 1, 3, 12);
        var arquivado = segmento(assuntoId(alvo.chave(1)), 2, 13, 20);
        jdbc.update("update segmento set arquivado_em = now() where id = ?", arquivado);

        mockMvc.perform(comToken(get("/internal/provas/{id}/julgamento", prova.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questoes.length()").value(1))
            .andExpect(jsonPath("$.questoes[0].numero").value(1))
            .andExpect(jsonPath("$.questoes[0].respostaGabarito").value("C"))
            .andExpect(jsonPath("$.questoes[0].alternativas.length()").value(5))
            .andExpect(jsonPath("$.questoes[0].assunto.chaveExterna").value(alvo.chave(1)))
            .andExpect(jsonPath("$.questoes[0].assunto.segmentos.length()").value(1))
            .andExpect(jsonPath("$.questoes[0].assunto.segmentos[0].id").value(ativo))
            .andExpect(jsonPath("$.questoes[0].assunto.segmentos[0].paginaInicial").value(3));
    }

    @Test
    void julgamento_foraDoEstado_409() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, null);

        mockMvc.perform(comToken(get("/internal/provas/{id}/julgamento", prova.getId())))
            .andExpect(status().isConflict());
    }
}
