package br.com.estudos.sessao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * D-45 (ADR-031): reenviar a mesma tentativa devolve sucesso com o resultado
 * original, nunca erro, e grava só uma vez. Item 3.6 de PROGRESSO.md.
 *
 * Sem @Transactional (herdado de IntegracaoTestBase): {@link SessaoService#registrar}
 * usa duas transações reais e separadas por baixo (gravar + buscarPorTentativa,
 * via self-injeção — ver o porquê no Javadoc do método) para não reaproveitar
 * uma sessão do Hibernate corrompida por um flush que falhou. Envolver o teste
 * numa transação também juntaria as duas de novo e mascararia exatamente o bug
 * que este teste existe para pegar.
 */
class SessaoIdempotenciaTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Autowired
    private SessaoRepository sessaoRepository;

    @Test
    void reenviarMesmaTentativa_devolveMesmoIdEGravaUmaVezSo() throws Exception {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Idempotencia Teste", TipoPeso.MEDIO));
        var assunto = assuntoService.criar(
            new AssuntoRequest(disciplina.getId(), "Assunto Idempotencia Teste", TipoPeso.MEDIO, (short) 3, 1, null));
        var tentativaId = UUID.randomUUID();

        var corpo = """
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "2026-08-30", "tempoMinutos": 25,
             "tentativaId": "%s"}
            """.formatted(assunto.getId(), tentativaId);

        var primeiraResposta = mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        var segundaResposta = mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        assertEquals(primeiraResposta, segundaResposta, "reenvio da mesma tentativa tem que devolver exatamente o mesmo corpo");
        assertEquals(1, sessaoRepository.findByAssuntoId(assunto.getId()).size(), "só pode existir uma linha gravada");
    }
}
