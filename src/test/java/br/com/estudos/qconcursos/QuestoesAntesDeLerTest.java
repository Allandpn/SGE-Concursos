package br.com.estudos.qconcursos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.frente.FrenteService;
import br.com.estudos.metrica.JanelaMetrica;
import br.com.estudos.metrica.MetricaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.FaseAssunto;
import br.com.estudos.shared.enums.TipoPeso;

/** D-155 (docs/SPRINT-36-QCONCURSOS.md §4): questões antes de ler contam nas métricas e não abrem a escada. */
@Transactional
class QuestoesAntesDeLerTest extends IntegracaoTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;
    @Autowired FrenteService frente;
    @Autowired MetricaService metricas;

    long disciplina;

    long novoAssunto(String nome) {
        return assuntos.criar(new AssuntoRequest(disciplina, nome, TipoPeso.MEDIO, (short) 3, (int) (System.nanoTime() % 100000), null)).getId();
    }

    void registrar(String corpo) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isCreated());
    }

    void questoes(long assunto, LocalDate data) throws Exception {
        registrar("""
            {"assuntoId": %d, "tipo": "QUESTOES", "data": "%s", "tempoMinutos": 24, "questoesCorretas": 7, "questoesTotal": 8,
             "formato": "MULTIPLA_ESCOLHA", "previsaoPercentual": 70, "tentativaId": "%s"}""".formatted(assunto, data, UUID.randomUUID()));
    }

    int revisoes(long assunto) {
        return jdbc.queryForObject("SELECT count(*) FROM revisao WHERE assunto_id = ?", Integer.class, assunto);
    }

    @Test
    void d155_nuncaLido_contaNaM1_semEscada_eSegueNoBacklog() throws Exception {
        disciplina = disciplinas.criar(new DisciplinaRequest("Antes de ler " + System.nanoTime(), TipoPeso.MEDIO)).getId();
        var assunto = novoAssunto("Nunca lido");
        questoes(assunto, LocalDate.now());

        assertThat(revisoes(assunto)).isZero();
        assertThat(frente.fase(assunto)).isEqualTo(FaseAssunto.BACKLOG);
        var linha = metricas.m1(JanelaMetrica.POR_DISCIPLINA).linhas().stream()
            .filter(l -> Long.valueOf(disciplina).equals(l.disciplinaId())).findFirst().orElseThrow();
        assertThat(linha.acertos()).isEqualTo(7);
        assertThat(linha.total()).isEqualTo(8);
    }

    @Test
    void d155_depoisDeLer_questoesSemRevisaoAbremAEscadaComoAntes() throws Exception {
        disciplina = disciplinas.criar(new DisciplinaRequest("Depois de ler " + System.nanoTime(), TipoPeso.MEDIO)).getId();
        var lido = novoAssunto("Lido");
        registrar("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 20, "tentativaId": "%s"}"""
            .formatted(lido, LocalDate.now().minusDays(1), UUID.randomUUID()));
        var antes = revisoes(lido);
        jdbc.update("UPDATE revisao SET situacao = 'CANCELADA' WHERE assunto_id = ?", lido);
        questoes(lido, LocalDate.now());
        assertThat(revisoes(lido)).isGreaterThan(antes);
        assertThat(frente.fase(lido)).isEqualTo(FaseAssunto.FRENTE);
    }
}
