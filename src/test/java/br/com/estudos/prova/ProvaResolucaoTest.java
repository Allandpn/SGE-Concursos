package br.com.estudos.prova;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.estudos.prova.ProvaInterna.GabaritoLido;
import br.com.estudos.prova.ProvaInterna.GabaritoRequest;
import br.com.estudos.prova.ProvaInterna.ItemVeredito;
import br.com.estudos.prova.ProvaInterna.QuestaoLida;
import br.com.estudos.prova.ProvaInterna.RespostaGabarito;
import br.com.estudos.prova.ProvaInterna.VereditosRequest;
import br.com.estudos.prova.ProvaPublica.AbrirTentativaRequest;
import br.com.estudos.shared.enums.SituacaoGabarito;
import br.com.estudos.shared.enums.VereditoCobertura;

/**
 * Resolver a prova (J-7; docs/SPRINT-24-PROVA-GABARITO.md §4, §7, §10): D-78, D-80, D-81, D-84.
 * A nota entra em M-1 por disciplina e só na primeira tentativa; nada vira sessão nem revisão.
 */
class ProvaResolucaoTest extends ProvaTestBase {

    /** Dois concursos-alvo, duas disciplinas: A (assuntos 1 e 2) e B (assunto 3). */
    private Alvo alvoDuasDisciplinas() throws Exception {
        var s = Long.toString(System.nanoTime());
        var csv = """
            disciplina,assunto,peso,ordem,chaveExterna
            DiscA %1$s,Primeiro %1$s,ALTO,1,p1-%1$s
            DiscA %1$s,Segundo %1$s,MEDIO,2,p2-%1$s
            DiscB %1$s,Terceiro %1$s,BAIXO,1,p3-%1$s
            """.formatted(s);
        var json = "{\"slug\": \"resolucao-%1$s\", \"nome\": \"Resolução %1$s\"}".formatted(s);
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar")
                .file(new MockMultipartFile("arquivo", "assuntos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("concurso", "concurso.json", "application/json", json.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk());
        Integer id = JsonPath.read(mockMvc.perform(get("/api/concursos/ativo")).andReturn().getResponse().getContentAsString(), "$.id");
        return new Alvo(id, s);
    }

    /**
     * Seis questões: 1 e 2 no assunto 1 (disciplina A), 3 no assunto 3 (disciplina B), 4 fora do edital,
     * 5 anulada pelo gabarito, 6 sem texto. Gabarito preliminar A, B, C, D, *, B.
     */
    private Prova prova6(Alvo alvo) {
        var prova = prova(alvo, "gabarito.pdf");
        var questoes = List.of(questao(1, alvo.chave(1)), questao(2, alvo.chave(1)), questao(3, alvo.chave(3)),
            questao(4, null), questao(5, alvo.chave(2)),
            new QuestaoLida(6, null, null, null, null, null, false, true, 2, null));
        provaService.entregarLeitura(prova.getId(), leitura(questoes, gabarito(SituacaoGabarito.PRELIMINAR, "A", "B", "C", "D", "*", "B")));
        return prova;
    }

    @AfterEach
    void limparOQueOsTestesCriaram() {
        // O banco é compartilhado: SimuladoDominioTest conta os simulados que existem.
        var simulados = jdbc.queryForList("select simulado_id from tentativa_prova where simulado_id is not null", Long.class);
        jdbc.update("delete from resposta_prova");
        jdbc.update("delete from tentativa_prova");
        simulados.forEach(id -> {
            jdbc.update("delete from resultado_simulado where simulado_id = ?", id);
            jdbc.update("delete from simulado where id = ?", id);
        });
    }

    private ResultActions abrir(Prova prova, UUID tentativa) throws Exception {
        return mockMvc.perform(post("/api/provas/{id}/tentativas", prova.getId()).contentType(MediaType.APPLICATION_JSON)
            .content(corpo(new AbrirTentativaRequest(tentativa, false, null))));
    }

    private void responder(Prova prova, UUID tentativa, int numero, String alternativa) throws Exception {
        mockMvc.perform(put("/api/provas/{id}/tentativas/{t}/respostas/{n}", prova.getId(), tentativa, numero)
                .contentType(MediaType.APPLICATION_JSON).content("{\"alternativa\": \"" + alternativa + "\"}"))
            .andExpect(status().isNoContent());
    }

    private ResultActions entregar(Prova prova, UUID tentativa) throws Exception {
        return mockMvc.perform(post("/api/provas/{id}/tentativas/{t}/entregar", prova.getId(), tentativa));
    }

    private long simulados() {
        return jdbc.queryForObject("select count(*) from simulado", Long.class);
    }

    /** 1 certa, 2 errada, 3 certa, 4 e 5 e 6 fora da nota: 2 de 3. */
    private void responderComoNoExemplo(Prova prova, UUID t) throws Exception {
        responder(prova, t, 1, "A");
        responder(prova, t, 2, "E");
        responder(prova, t, 3, "C");
        responder(prova, t, 4, "D");
        responder(prova, t, 5, "A");
        responder(prova, t, 6, "B");
    }

    // ------------------------------------------------------------ abrir e retomar

    @Test
    void d81_semGabarito_naoResolve() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova(alvo, null);
        provaService.entregarLeitura(prova.getId(), leitura(List.of(questao(1, alvo.chave(1))), null));

        abrir(prova, UUID.randomUUID())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("PROVA_SEM_GABARITO"));
    }

    @Test
    void d80_mesmaTentativaContaUmaVez() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var tentativa = UUID.randomUUID();

        var primeira = abrir(prova, tentativa).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        abrir(prova, tentativa).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value((int) JsonPath.read(primeira, "$.id")));
        assertEquals(1L, jdbc.queryForObject("select count(*) from tentativa_prova where tentativa_id = ?::uuid", Long.class, tentativa.toString()));

        var outra = prova6(alvo);
        abrir(outra, tentativa).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("TENTATIVA_DE_OUTRA_PROVA"));
    }

    // ------------------------------------------------------------ resolver

    @Test
    void d80_gabaritoNaoSaiAntesDaEntrega() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var t = UUID.randomUUID();
        abrir(prova, t).andExpect(status().isCreated());
        responder(prova, t, 1, "A");

        mockMvc.perform(get("/api/provas/{id}/tentativas/{t}", prova.getId(), t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.questoes.length()").value(6))
            .andExpect(jsonPath("$.questoes[0].alternativaMarcada").value("A"))
            .andExpect(jsonPath("$.correcao.length()").value(0))
            // A questão sem texto aparece, sinalizada, para o usuário ver a página dela (D-85).
            .andExpect(jsonPath("$.questoes[5].avaliavel").value(false))
            .andExpect(jsonPath("$.questoes[5].paginaOrigem").value(2))
            // O contrato não tem a resposta do gabarito: nem o nome do campo existe na resposta.
            .andExpect(content().string(not(containsString("respostaGabarito"))));
    }

    @Test
    void responder_validaALetra_marcaRevisar_eNaoMudaDepoisDeEntregar() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var t = UUID.randomUUID();
        abrir(prova, t).andExpect(status().isCreated());

        mockMvc.perform(put("/api/provas/{id}/tentativas/{t}/respostas/{n}", prova.getId(), t, 1)
                .contentType(MediaType.APPLICATION_JSON).content("{\"alternativa\": \"Z\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("LETRA_INVALIDA"));
        mockMvc.perform(put("/api/provas/{id}/tentativas/{t}/respostas/{n}", prova.getId(), t, 99)
                .contentType(MediaType.APPLICATION_JSON).content("{\"alternativa\": \"A\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("QUESTAO_INEXISTENTE"));
        mockMvc.perform(put("/api/provas/{id}/tentativas/{t}/respostas/{n}", prova.getId(), t, 2)
                .contentType(MediaType.APPLICATION_JSON).content("{\"alternativa\": null, \"marcadaRevisar\": true}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/provas/{id}/tentativas/{t}", prova.getId(), t))
            .andExpect(jsonPath("$.questoes[1].marcadaRevisar").value(true))
            .andExpect(jsonPath("$.tentativa.respondidas").value(0));

        responder(prova, t, 2, "B");
        responder(prova, t, 2, "C"); // mudar de ideia antes de entregar vale
        mockMvc.perform(get("/api/provas/{id}/tentativas/{t}", prova.getId(), t))
            .andExpect(jsonPath("$.questoes[1].alternativaMarcada").value("C"))
            .andExpect(jsonPath("$.tentativa.respondidas").value(1));

        entregar(prova, t).andExpect(status().isOk());
        mockMvc.perform(put("/api/provas/{id}/tentativas/{t}/respostas/{n}", prova.getId(), t, 2)
                .contentType(MediaType.APPLICATION_JSON).content("{\"alternativa\": \"A\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("TENTATIVA_ENTREGUE"));
    }

    // ------------------------------------------------------------ entregar

    @Test
    void entregar_corrigePeloGabarito_criaSimuladoPorDisciplina() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var t = UUID.randomUUID();
        abrir(prova, t).andExpect(status().isCreated());
        responderComoNoExemplo(prova, t);
        var antes = simulados();

        entregar(prova, t)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contaParaM1").value(true))
            .andExpect(jsonPath("$.gabaritoPreliminar").value(true))
            // Os dois números (D-71): 2 acertos de 3 questões na nota.
            .andExpect(jsonPath("$.global.corretas").value(2))
            .andExpect(jsonPath("$.global.total").value(3))
            .andExpect(jsonPath("$.porDisciplina.length()").value(2))
            .andExpect(jsonPath("$.porDisciplina[0].placar.corretas").value(1))
            .andExpect(jsonPath("$.porDisciplina[0].placar.total").value(2))
            .andExpect(jsonPath("$.porDisciplina[1].placar.corretas").value(1))
            .andExpect(jsonPath("$.porDisciplina[1].placar.total").value(1))
            // Fora do edital, anulada e sem texto ficam fora da nota e vêm contadas (D-70).
            .andExpect(jsonPath("$.foraDaNota.foraDoEdital").value(1))
            .andExpect(jsonPath("$.foraDaNota.anuladas").value(1))
            .andExpect(jsonPath("$.foraDaNota.semTexto").value(1))
            .andExpect(jsonPath("$.semResposta").value(0));

        assertEquals(antes + 1, simulados(), "um simulado criado (D-78)");
        var linhas = jdbc.queryForList("""
            select r.questoes_corretas, r.questoes_total, r.formato from resultado_simulado r
            join tentativa_prova t on t.simulado_id = r.simulado_id where t.tentativa_id = ?::uuid
            order by r.disciplina_id""", t.toString());
        assertEquals(2, linhas.size(), "uma apuração por disciplina, nunca por assunto (D-13)");
        assertEquals("MULTIPLA_ESCOLHA", linhas.get(0).get("formato"));
        assertEquals(1, ((Number) linhas.get(0).get("questoes_corretas")).intValue());
        assertEquals(2, ((Number) linhas.get(0).get("questoes_total")).intValue());
    }

    @Test
    void entregar_questaoSemResposta_contaNoTotalENaoComoAcerto() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var t = UUID.randomUUID();
        abrir(prova, t).andExpect(status().isCreated());
        responder(prova, t, 1, "A");

        entregar(prova, t)
            .andExpect(jsonPath("$.global.corretas").value(1))
            .andExpect(jsonPath("$.global.total").value(3))
            .andExpect(jsonPath("$.semResposta").value(2));
    }

    @Test
    void d80_entregarDeNovo_devolveAMesmaNota_semOutroSimulado() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var t = UUID.randomUUID();
        abrir(prova, t).andExpect(status().isCreated());
        responderComoNoExemplo(prova, t);
        var antes = simulados();

        var primeira = entregar(prova, t).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var segunda = entregar(prova, t).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals((int) JsonPath.read(primeira, "$.global.corretas"), (int) JsonPath.read(segunda, "$.global.corretas"));
        assertEquals(antes + 1, simulados(), "entregar duas vezes não cria dois simulados");
    }

    @Test
    void d78_segundaTentativa_naoAlimentaM1() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var primeira = UUID.randomUUID();
        var segunda = UUID.randomUUID();
        abrir(prova, primeira).andExpect(status().isCreated());
        abrir(prova, segunda).andExpect(status().isCreated());
        responderComoNoExemplo(prova, primeira);
        responder(prova, segunda, 1, "A");
        responder(prova, segunda, 2, "B");
        responder(prova, segunda, 3, "C");
        var antes = simulados();

        entregar(prova, primeira).andExpect(jsonPath("$.contaParaM1").value(true));
        entregar(prova, segunda)
            .andExpect(jsonPath("$.contaParaM1").value(false))
            .andExpect(jsonPath("$.global.corretas").value(3));

        assertEquals(antes + 1, simulados(), "só a primeira tentativa entra em M-1 (D-78)");
        assertEquals(1L, jdbc.queryForObject("select count(*) from tentativa_prova where prova_id = ? and simulado_id is not null", Long.class, prova.getId()));
    }

    @Test
    void d78_entregarNaoCriaSessaoNemRevisao() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var t = UUID.randomUUID();
        abrir(prova, t).andExpect(status().isCreated());
        responderComoNoExemplo(prova, t);
        var sessoes = jdbc.queryForObject("select count(*) from sessao", Long.class);
        var revisoes = jdbc.queryForObject("select count(*) from revisao", Long.class);

        entregar(prova, t).andExpect(status().isOk());

        assertEquals(sessoes, jdbc.queryForObject("select count(*) from sessao", Long.class), "a nota não gera sessão");
        assertEquals(revisoes, jdbc.queryForObject("select count(*) from revisao", Long.class), "nem revisão");
    }

    // ------------------------------------------------------------ gabarito definitivo (D-84)

    private GabaritoRequest definitivo(String... respostas) {
        var lista = new java.util.ArrayList<RespostaGabarito>();
        for (var i = 0; i < respostas.length; i++) lista.add(new RespostaGabarito(i + 1, respostas[i], null));
        return new GabaritoRequest("m", new GabaritoLido(SituacaoGabarito.DEFINITIVO, "cargo X, tipo 1", lista));
    }

    @Test
    void d84_respostaMudou_recalculaANotaQueConta() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var t = UUID.randomUUID();
        abrir(prova, t).andExpect(status().isCreated());
        responderComoNoExemplo(prova, t); // a 2 foi respondida E
        entregar(prova, t).andExpect(jsonPath("$.global.corretas").value(2));

        // O definitivo muda a resposta da questão 2 de B para E: o que o usuário marcou passa a estar certo.
        provaService.entregarGabarito(prova.getId(), definitivo("A", "E", "C", "D", "*", "B"));

        var linhas = jdbc.queryForList("""
            select r.questoes_corretas, r.questoes_total from resultado_simulado r
            join tentativa_prova t on t.simulado_id = r.simulado_id where t.tentativa_id = ?::uuid
            order by r.disciplina_id""", t.toString());
        assertEquals(2, ((Number) linhas.get(0).get("questoes_corretas")).intValue(), "disciplina A: de 1 de 2 para 2 de 2");
        assertEquals(2, ((Number) linhas.get(0).get("questoes_total")).intValue());
        entregar(prova, t).andExpect(jsonPath("$.global.corretas").value(3)).andExpect(jsonPath("$.gabaritoPreliminar").value(false));
    }

    @Test
    void d84_disciplinaSemQuestao_perdeAApuracao() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var t = UUID.randomUUID();
        abrir(prova, t).andExpect(status().isCreated());
        responderComoNoExemplo(prova, t);
        entregar(prova, t).andExpect(jsonPath("$.porDisciplina.length()").value(2));

        // O definitivo anula a questão 3, a única da disciplina B.
        provaService.entregarGabarito(prova.getId(), definitivo("A", "B", "*", "D", "*", "B"));

        assertEquals(1L, jdbc.queryForObject("""
            select count(*) from resultado_simulado r
            join tentativa_prova t on t.simulado_id = r.simulado_id where t.tentativa_id = ?::uuid""", Long.class, t.toString()),
            "a disciplina que perdeu todas as questões deixa de ter apuração");
    }

    // ------------------------------------------------------------ relatório e correção

    @Test
    void relatorio_trazANotaReal_asOutrasTentativas_eOndeCoberturaENotaDiscordam() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        var s1 = segmento(assuntoId(alvo.chave(1)), 1, 1, 10);
        var s3 = segmento(assuntoId(alvo.chave(3)), 1, 1, 10);
        jdbc.update("update prova set status = 'JULGANDO' where id = ?", prova.getId());
        provaService.registrarVereditos(prova.getId(), new VereditosRequest("m", List.of(
            new ItemVeredito(1, VereditoCobertura.RESPONDIVEL, s1, 2, null),
            new ItemVeredito(2, VereditoCobertura.RESPONDIVEL, s1, 3, null),
            new ItemVeredito(3, VereditoCobertura.NAO_RESPONDIVEL, null, null, "o material não traz o conceito"))));
        var t = UUID.randomUUID();
        abrir(prova, t).andExpect(status().isCreated());
        responderComoNoExemplo(prova, t); // 1 certa, 2 errada, 3 certa
        entregar(prova, t).andExpect(status().isOk());
        var outra = UUID.randomUUID();
        abrir(prova, outra).andExpect(status().isCreated());

        mockMvc.perform(get("/api/provas/{id}/relatorio", prova.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nota.global.corretas").value(2))
            .andExpect(jsonPath("$.nota.global.total").value(3))
            .andExpect(jsonPath("$.nota.contaParaM1").value(true))
            .andExpect(jsonPath("$.outrasTentativas.length()").value(1))
            // O assunto 1 parecia sólido (2 respondíveis) e o usuário errou uma; o 3 parecia furado e ele acertou.
            .andExpect(jsonPath("$.discordancias.length()").value(2))
            .andExpect(jsonPath("$.discordancias[0].tipo").value("SOLIDA_E_ERROU"))
            .andExpect(jsonPath("$.discordancias[0].acertos").value(1))
            .andExpect(jsonPath("$.discordancias[0].questoes").value(2))
            .andExpect(jsonPath("$.discordancias[1].tipo").value("FURADA_E_ACERTOU"));

        // O erro mostra o gabarito e a página que sustentava (J-7 passo 4) — só depois da entrega.
        mockMvc.perform(get("/api/provas/{id}/tentativas/{t}", prova.getId(), t))
            .andExpect(jsonPath("$.correcao.length()").value(6))
            .andExpect(jsonPath("$.correcao[1].respostaGabarito").value("B"))
            .andExpect(jsonPath("$.correcao[1].alternativaMarcada").value("E"))
            .andExpect(jsonPath("$.correcao[1].acertou").value(false))
            .andExpect(jsonPath("$.correcao[1].segmentoId").value(s1))
            .andExpect(jsonPath("$.correcao[1].paginaSegmento").value(3))
            .andExpect(jsonPath("$.correcao[0].acertou").value(true))
            // Fora da nota: sem "acertou", e a razão é visível.
            .andExpect(jsonPath("$.correcao[3].valeNaNota").value(false))
            .andExpect(jsonPath("$.correcao[3].acertou").doesNotExist());
    }

    @Test
    void relatorio_semTentativaEntregue_naoTemNota() throws Exception {
        var alvo = alvoDuasDisciplinas();
        var prova = prova6(alvo);
        jdbc.update("update prova set status = 'JULGANDO' where id = ?", prova.getId());

        mockMvc.perform(get("/api/provas/{id}/relatorio", prova.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nota").doesNotExist())
            .andExpect(jsonPath("$.outrasTentativas.length()").value(0))
            .andExpect(jsonPath("$.discordancias.length()").value(0));
    }
}
