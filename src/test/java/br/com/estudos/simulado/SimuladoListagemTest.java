package br.com.estudos.simulado;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * GET /api/simulados fora de transação de teste (sem @Transactional na
 * classe) — ADR-034 (open-in-view: false) exige join fetch em
 * ResultadoSimuladoRepository.findBySimuladoIdIn; sem isto a serialização do
 * Controller lançaria LazyInitializationException aqui. SimuladoDominioTest
 * é @Transactional e não pegaria essa regressão, mesma razão documentada em
 * ADR-034. Sem contagem absoluta de propósito (03_INVARIANTES §10 — evita a
 * mesma armadilha do changelog 1.14.0: outra classe não-transacional que
 * commite em `simulado` no futuro não quebra este teste).
 */
class SimuladoListagemTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Test
    void lista_semLazyInitializationException() throws Exception {
        var disciplina = disciplinaService.criar(
            new DisciplinaRequest("Disciplina Simulado Listagem " + UUID.randomUUID(), TipoPeso.MEDIO));

        mockMvc.perform(post("/api/simulados")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"data": "2026-06-01", "duracaoMinutos": 240,
                     "resultados": [{"disciplinaId": %d, "formato": "MULTIPLA_ESCOLHA", "questoesCorretas": 10, "questoesTotal": 20}]}
                    """.formatted(disciplina.getId())))
            .andExpect(status().isCreated());

        var resposta = mockMvc.perform(get("/api/simulados"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(resposta.contains(disciplina.getId().toString()));
    }
}