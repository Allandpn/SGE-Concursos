package br.com.estudos.sessao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.segmento.SegmentoRequest;
import br.com.estudos.segmento.SegmentoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Um teste por erro de domínio da tabela de docs/SPRINT-3-SESSAO.md §3.1
 * (incluindo as duas validações eager sem restrição de banco correspondente)
 * — item 3.6 de PROGRESSO.md. @Transactional: só confere HTTP/JSON, nenhum
 * destes cenários faz uma segunda chamada de service depois da falha (ao
 * contrário de D-45), então não corre o risco de autoinvocação/sessão do
 * Hibernate corrompida que SessaoIdempotenciaTest evita não usando isto.
 */
@Transactional
class SessaoErroDominioTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Autowired
    private SegmentoService segmentoService;

    private Long assuntoId() {
        return assuntoId("");
    }

    // sufixo: cada teste que precisa de DOIS assuntos na mesma transação
    // (D-51) não pode repetir "Disciplina Sessao Teste" — colidiria em
    // NOME_DUPLICADO antes de chegar no que o teste quer provar.
    private Long assuntoId(String sufixo) {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Sessao Teste" + sufixo, TipoPeso.MEDIO));
        var assunto = assuntoService.criar(
            new AssuntoRequest(disciplina.getId(), "Assunto Sessao Teste" + sufixo, TipoPeso.MEDIO, (short) 3, 1, null));
        return assunto.getId();
    }

    @Test
    void assuntoInexistente_devolve404() throws Exception {
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": 999999, "tipo": "ESTUDO", "data": "2026-08-30", "tempoMinutos": 10,
                     "tentativaId": "11111111-1111-1111-1111-111111111111"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("ASSUNTO_INEXISTENTE"));
    }

    @Test
    void estudoComResultado_devolve422() throws Exception {
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "ESTUDO", "data": "2026-08-30", "tempoMinutos": 10,
                     "resultado": "SUCESSO", "tentativaId": "22222222-2222-2222-2222-222222222222"}
                    """.formatted(assuntoId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("ESTUDO_SEM_RESULTADO"));
    }

    @Test
    void recuperacaoComPrevisaoErrada_devolve422() throws Exception {
        // RECUPERACAO usa previsaoReconstrucao (ResultadoSessao), não previsaoPercentual — D-04a.
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "RECUPERACAO", "data": "2026-08-30", "tempoMinutos": 8,
                     "previsaoPercentual": 50, "resultado": "SUCESSO",
                     "tentativaId": "33333333-3333-3333-3333-333333333333"}
                    """.formatted(assuntoId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("PREVISAO_INVALIDA"));
    }

    @Test
    void questoesSemFormato_devolve422() throws Exception {
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "QUESTOES", "data": "2026-08-30", "tempoMinutos": 10,
                     "questoesCorretas": 5, "questoesTotal": 10, "previsaoPercentual": 50,
                     "tentativaId": "44444444-4444-4444-4444-444444444444"}
                    """.formatted(assuntoId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("FORMATO_OBRIGATORIO"));
    }

    @Test
    void estudoComFormato_devolve422() throws Exception {
        // ck_sessao_d36_questoes_tem_formato sozinha só exige formato PRESENTE em QUESTOES —
        // sem ck_sessao_formato_por_tipo, formato preenchido em ESTUDO passava (achado manualmente).
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "ESTUDO", "data": "2026-08-30", "tempoMinutos": 10,
                     "formato": "CERTO_ERRADO", "tentativaId": "66666666-6666-6666-6666-666666666666"}
                    """.formatted(assuntoId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("FORMATO_NAO_APLICAVEL"));
    }

    @Test
    void flashcardsSemContagem_devolve422() throws Exception {
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "FLASHCARDS", "data": "2026-08-30", "tempoMinutos": 10,
                     "previsaoPercentual": 50, "tentativaId": "55555555-5555-5555-5555-555555555555"}
                    """.formatted(assuntoId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("QUESTOES_OBRIGATORIAS"));
    }

    @Test
    void recuperacaoSemResultado_devolve422() throws Exception {
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "RECUPERACAO", "data": "2026-08-30", "tempoMinutos": 8,
                     "previsaoReconstrucao": "SUCESSO", "tentativaId": "66666666-6666-6666-6666-666666666666"}
                    """.formatted(assuntoId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("RESULTADO_OBRIGATORIO"));
    }

    @Test
    void segmentoDeOutroAssunto_devolve409() throws Exception {
        var assuntoDoSegmento = assuntoId("SegA");
        var outroAssunto = assuntoId("SegB");
        var segmento = segmentoService.criarOuAtualizar(new SegmentoRequest(
            assuntoDoSegmento, UUID.randomUUID().toString(), 1, "https://drive.example/segmento-a",
            null, null, null));

        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "ESTUDO", "data": "2026-08-30", "tempoMinutos": 10,
                     "tentativaId": "77777777-7777-7777-7777-777777777777", "segmentoId": %d}
                    """.formatted(outroAssunto, segmento.getId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("SEGMENTO_DE_OUTRO_ASSUNTO"));
    }

    @Test
    void segmentoForaDeEstudo_devolve422() throws Exception {
        var id = assuntoId("SegC");
        var segmento = segmentoService.criarOuAtualizar(new SegmentoRequest(
            id, UUID.randomUUID().toString(), 1, "https://drive.example/segmento-c", null, null, null));

        // previsaoPercentual precisa vir preenchido: QUESTOES sempre calcula
        // resultado no serviço, e D-04a exige previsaoPercentual presente
        // quando resultado não é nulo — sem isso, a linha cairia em
        // PREVISAO_INVALIDA antes de chegar em D-51 (achado rodando o teste).
        mockMvc.perform(post("/api/sessoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "tipo": "QUESTOES", "data": "2026-08-30", "tempoMinutos": 10,
                     "questoesCorretas": 5, "questoesTotal": 10, "formato": "MULTIPLA_ESCOLHA",
                     "previsaoPercentual": 50,
                     "tentativaId": "88888888-8888-8888-8888-888888888888", "segmentoId": %d}
                    """.formatted(id, segmento.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("SEGMENTO_FORA_DE_ESTUDO"));
    }
}
