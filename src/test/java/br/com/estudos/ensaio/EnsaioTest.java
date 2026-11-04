package br.com.estudos.ensaio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jayway.jsonpath.JsonPath;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * D-159 e ADR-059 (docs/SPRINT-38-PROXIMOS-DIAS-E-ENSAIO.md §4). Sem {@code @Transactional} na classe: o ensaio precisa da própria
 * transação, que ele desfaz, para o teste ver o banco igual depois; o que o registro real grava é apagado no fim.
 */
class EnsaioTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.now();

    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;

    final List<Long> criados = new ArrayList<>();
    Long disciplina;

    @AfterEach
    void limpar() {
        for (var a : criados) {
            jdbc.update("DELETE FROM erro WHERE assunto_id = ?", a);
            jdbc.update("DELETE FROM revisao WHERE assunto_id = ?", a);
            jdbc.update("DELETE FROM sessao WHERE assunto_id = ?", a);
            jdbc.update("DELETE FROM assunto WHERE id = ?", a);
        }
        if (disciplina != null) jdbc.update("DELETE FROM disciplina WHERE id = ?", disciplina);
    }

    long assunto() {
        if (disciplina == null) {
            disciplina = disciplinas.criar(new DisciplinaRequest("Ensaio " + System.nanoTime(), TipoPeso.MEDIO)).getId();
        }
        var id = assuntos.criar(new AssuntoRequest(disciplina, "Assunto " + System.nanoTime(), TipoPeso.MEDIO, (short) 3,
            criados.size() + 1, null)).getId();
        criados.add(id);
        return id;
    }

    String estudo(long assunto, LocalDate data) {
        return """
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 30, "tentativaId": "%s"}""".formatted(assunto, data, UUID.randomUUID());
    }

    String questoes(long assunto, int certas, int total) {
        return """
            {"assuntoId": %d, "tipo": "QUESTOES", "data": "%s", "tempoMinutos": 20, "questoesCorretas": %d, "questoesTotal": %d,
             "formato": "MULTIPLA_ESCOLHA", "previsaoPercentual": 70, "tentativaId": "%s"}""".formatted(assunto, HOJE, certas, total, UUID.randomUUID());
    }

    String recuperacao(long assunto, String resultado) {
        return """
            {"assuntoId": %d, "tipo": "RECUPERACAO", "data": "%s", "tempoMinutos": 8, "previsaoReconstrucao": "SUCESSO",
             "resultado": "%s", "tentativaId": "%s"}""".formatted(assunto, HOJE, resultado, UUID.randomUUID());
    }

    void registrar(String corpo) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated());
    }

    String ensaiar(String corpo) throws Exception {
        return mockMvc.perform(post("/api/sessoes/ensaio").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** O retrato do que o ensaio não pode mudar. */
    String retrato(long assunto) {
        return jdbc.queryForObject("SELECT count(*) FROM sessao", Long.class) + "|"
            + jdbc.queryForObject("SELECT count(*) FROM revisao", Long.class) + "|"
            + jdbc.queryForObject("SELECT count(*) FROM sessao_pergunta", Long.class) + "|"
            + jdbc.queryForObject("SELECT count(*) FROM pergunta_preparo", Long.class) + "|"
            + jdbc.queryForObject("SELECT count(*) FROM erro", Long.class) + "|"
            + jdbc.queryForObject("""
                SELECT coalesce(string_agg(id || ':' || situacao || ':' || nivel || ':' || data_prevista || ':' || versao, ',' ORDER BY id), '')
                FROM revisao WHERE assunto_id = ?""", String.class, assunto);
    }

    String pendente(long assunto) {
        return jdbc.queryForObject("SELECT nivel || ' ' || data_prevista FROM revisao WHERE assunto_id = ? AND situacao = 'PENDENTE'",
            String.class, assunto);
    }

    @Test
    void d159_questoes_oEnsaioPreveOQueORegistroReal_fazENadaFicaGravado() throws Exception {
        var a = assunto();
        registrar(estudo(a, HOJE.minusDays(1))); // nível 1 vence hoje
        var antes = retrato(a);

        var corpo = ensaiar(questoes(a, 9, 10));
        assertThat(retrato(a)).isEqualTo(antes);
        assertThat((String) JsonPath.read(corpo, "$.efeito")).isEqualTo("CUMPRE");
        assertThat((String) JsonPath.read(corpo, "$.resultado")).isEqualTo("SUCESSO");
        assertThat((Integer) JsonPath.read(corpo, "$.percentual")).isEqualTo(90);
        var previsto = JsonPath.read(corpo, "$.proxima.nivel") + " " + JsonPath.read(corpo, "$.proxima.dataPrevista");

        registrar(questoes(a, 9, 10));
        assertThat(pendente(a)).isEqualTo(previsto);
    }

    @Test
    void d159_recuperacao_igualAoRegistroReal() throws Exception {
        var a = assunto();
        registrar(estudo(a, HOJE.minusDays(1)));
        var antes = retrato(a);
        var corpo = ensaiar(recuperacao(a, "PARCIAL"));
        assertThat(retrato(a)).isEqualTo(antes);
        var previsto = JsonPath.read(corpo, "$.proxima.nivel") + " " + JsonPath.read(corpo, "$.proxima.dataPrevista");
        registrar(recuperacao(a, "PARCIAL"));
        assertThat(pendente(a)).isEqualTo(previsto);
    }

    @Test
    void d159_d155_nuncaLido_loteMinimo_eEstudo_semGravar() throws Exception {
        var nunca = assunto();
        var antes = retrato(nunca);
        mockMvc.perform(post("/api/sessoes/ensaio").contentType(MediaType.APPLICATION_JSON).content(questoes(nunca, 8, 10)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.efeito").value("SEM_MUDANCA"))
            .andExpect(jsonPath("$.motivo").value("NUNCA_LIDO"))
            .andExpect(jsonPath("$.proxima").value(org.hamcrest.Matchers.nullValue()));
        ensaiar(estudo(nunca, HOJE));
        assertThat(retrato(nunca)).isEqualTo(antes);

        var lido = assunto();
        registrar(estudo(lido, HOJE.minusDays(1)));
        mockMvc.perform(post("/api/sessoes/ensaio").contentType(MediaType.APPLICATION_JSON).content(questoes(lido, 3, 3)))
            .andExpect(jsonPath("$.efeito").value("SEM_MUDANCA"))
            .andExpect(jsonPath("$.motivo").value("LOTE_PEQUENO"));

        // Erro de validação é o mesmo do registro, e também não grava nada.
        var depois = retrato(lido);
        mockMvc.perform(post("/api/sessoes/ensaio").contentType(MediaType.APPLICATION_JSON).content("""
                {"assuntoId": %d, "tipo": "QUESTOES", "data": "%s", "tempoMinutos": 20, "formato": "MULTIPLA_ESCOLHA",
                 "tentativaId": "%s"}""".formatted(lido, HOJE, UUID.randomUUID())))
            .andExpect(status().is4xxClientError());
        assertThat(retrato(lido)).isEqualTo(depois);
    }
}
