package br.com.estudos.sessao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Resultado calculado pelo serviço a partir de `parametro` (QUESTOES/FLASHCARDS)
 * ou declarado pelo cliente (RECUPERACAO) — docs/SPRINT-3-SESSAO.md §3.2.
 * Item 3.6 de PROGRESSO.md.
 */
@Transactional
class SessaoResultadoTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    private Long assuntoId() {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Resultado Teste", TipoPeso.MEDIO));
        var assunto = assuntoService.criar(
            new AssuntoRequest(disciplina.getId(), "Assunto Resultado Teste " + System.nanoTime(), TipoPeso.MEDIO, (short) 3, 1, null));
        return assunto.getId();
    }

    @Test
    void questoesMultiplaEscolha_85porcento_sucesso() throws Exception {
        registrarQuestoes(17, 20, "MULTIPLA_ESCOLHA", "SUCESSO"); // limiar sucesso = 80
    }

    @Test
    void questoesMultiplaEscolha_65porcento_parcial() throws Exception {
        registrarQuestoes(13, 20, "MULTIPLA_ESCOLHA", "PARCIAL"); // limiar parcial = 60..79
    }

    @Test
    void questoesMultiplaEscolha_40porcento_falha() throws Exception {
        registrarQuestoes(8, 20, "MULTIPLA_ESCOLHA", "FALHA");
    }

    @Test
    void questoesCertoErrado_80porcento_parcial() throws Exception {
        // limiar certo/errado é 90/75 — 80% é PARCIAL, não seria em múltipla escolha
        registrarQuestoes(8, 10, "CERTO_ERRADO", "PARCIAL");
    }

    @Test
    void flashcards_90porcento_sucesso() throws Exception {
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "FLASHCARDS", "data": "2026-08-30", "tempoMinutos": 10,
                     "questoesCorretas": 9, "questoesTotal": 10, "previsaoPercentual": 50,
                     "tentativaId": "%s"}
                    """.formatted(assuntoId(), java.util.UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resultado").value("SUCESSO"));
    }

    @Test
    void recuperacao_resultadoDeclaradoPersiste() throws Exception {
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "RECUPERACAO", "data": "2026-08-30", "tempoMinutos": 8,
                     "previsaoReconstrucao": "SUCESSO", "resultado": "PARCIAL",
                     "tentativaId": "%s"}
                    """.formatted(assuntoId(), java.util.UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resultado").value("PARCIAL"));
    }

    private void registrarQuestoes(int corretas, int total, String formato, String resultadoEsperado) throws Exception {
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "QUESTOES", "data": "2026-08-30", "tempoMinutos": 30,
                     "questoesCorretas": %d, "questoesTotal": %d, "formato": "%s", "previsaoPercentual": 50,
                     "tentativaId": "%s"}
                    """.formatted(assuntoId(), corretas, total, formato, java.util.UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resultado").value(resultadoEsperado));
    }
}
