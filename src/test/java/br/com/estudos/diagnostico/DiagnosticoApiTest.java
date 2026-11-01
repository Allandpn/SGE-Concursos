package br.com.estudos.diagnostico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.sessao.SessaoRequest;
import br.com.estudos.sessao.SessaoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.enums.TipoSessao;

/** D-137 e D-139 pela rota (docs/SPRINT-20-DIAGNOSTICO-EDITAL.md §4). */
class DiagnosticoApiTest extends IntegracaoTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;
    @Autowired SessaoService sessoes;

    long concurso;
    long nuncaAlto;
    long nuncaBaixo;
    LocalDate hoje = LocalDate.now();

    /**
     * Um concurso que não é o alvo. Disciplina 1 (2 pontos, 10 questões): nunca visto (ALTO), em estudo, em escada. Disciplina 2
     * (1 ponto, 5 questões): dois nunca vistos (MEDIO, BAIXO) e um arquivado.
     */
    @BeforeEach
    void cenario() {
        var n = System.nanoTime();
        var d1 = disciplinas.criar(new DisciplinaRequest("Diag BD " + n, TipoPeso.ALTO)).getId();
        var d2 = disciplinas.criar(new DisciplinaRequest("Diag Port " + n, TipoPeso.MEDIO)).getId();
        nuncaAlto = assunto(d1, "Normalização " + n);
        var estudo = assunto(d1, "SQL " + n);
        var escada = assunto(d1, "Índices " + n);
        var nuncaMedio = assunto(d2, "Crase " + n);
        nuncaBaixo = assunto(d2, "Regência " + n);
        var arquivado = assunto(d2, "Ortografia " + n);
        jdbc.update("UPDATE assunto SET ativo = false WHERE id = ?", arquivado);
        // Em estudo: sessão sem revisão nenhuma (D-54); em escada: o registro de estudo agenda a revisão.
        jdbc.update("INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, tentativa_id, ativo) VALUES (?, 'ESTUDO', current_date, 30, ?, true)",
            estudo, UUID.randomUUID());
        sessoes.registrar(new SessaoRequest(escada, TipoSessao.ESTUDO, hoje.minusDays(2), 50, null, null, null, null, null, null,
            UUID.randomUUID(), null));

        concurso = jdbc.queryForObject("""
            INSERT INTO concurso (nome, origem, slug, data_prova, ativo) VALUES ('Diag ' || ?, 'PLANEJAMENTO', 'diag-' || ?, ?, false)
            RETURNING id""", Long.class, n, n, hoje.plusDays(70));
        cobrir(nuncaAlto, d1, "ALTO", 1);
        cobrir(estudo, d1, "ALTO", 2);
        cobrir(escada, d1, "ALTO", 3);
        cobrir(nuncaMedio, d2, "MEDIO", 1);
        cobrir(nuncaBaixo, d2, "BAIXO", 2);
        cobrir(arquivado, d2, "BAIXO", 3);
        jdbc.update("INSERT INTO concurso_pontuacao (concurso_id, disciplina_id, pontos) VALUES (?, ?, 2)", concurso, d1);
        var analise = jdbc.queryForObject("""
            INSERT INTO analise_edital (nome_arquivo, texto, status, concurso_id) VALUES ('e.pdf', '', 'CONFIRMADA', ?) RETURNING id""",
            Long.class, concurso);
        // O nome da análise casa sem acento e sem caixa (DIAG BD, diag port).
        jdbc.update("INSERT INTO edital_disciplina (analise_id, ordem, nome, questoes) VALUES (?, 1, ?, 10), (?, 2, ?, 5)",
            analise, ("Diag BD " + n).toUpperCase(), analise, ("Diag Port " + n).toLowerCase());
    }

    int ordem;

    long assunto(long disciplina, String nome) {
        return assuntos.criar(new AssuntoRequest(disciplina, nome, TipoPeso.MEDIO, (short) 3, ++ordem, null)).getId();
    }

    void cobrir(long assunto, long disciplina, String peso, int ordem) {
        jdbc.update("INSERT INTO concurso_assunto (concurso_id, assunto_id, disciplina_id, peso, ordem) VALUES (?, ?, ?, ?, ?)",
            concurso, assunto, disciplina, peso, ordem);
    }

    @Test
    void d137_d138_coberturaPorFase_ePontos_deUmConcursoQueNaoEAlvo() throws Exception {
        mockMvc.perform(get("/api/concursos/{id}/diagnostico", concurso))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.arquivados").value(1))
            .andExpect(jsonPath("$.consolidado.assuntos").value(0))
            .andExpect(jsonPath("$.encaminhado.assuntos").value(1))
            // Disciplina 1 vale 2 × 10 = 20 em 3 assuntos; a 2 vale 5. O em escada vale 20/3 de 25 = 27%.
            .andExpect(jsonPath("$.encaminhado.pontosPct").value(27))
            .andExpect(jsonPath("$.disciplinas[0].questoes").value(10))
            .andExpect(jsonPath("$.disciplinas[0].nunca").value(1))
            .andExpect(jsonPath("$.disciplinas[0].estudo").value(1))
            .andExpect(jsonPath("$.disciplinas[0].escada").value(1))
            .andExpect(jsonPath("$.disciplinas[1].nunca").value(2))
            .andExpect(jsonPath("$.disciplinas[1].total").value(2))
            // Sem data simulada, vale a data da prova (futura).
            .andExpect(jsonPath("$.cabe.data").value(hoje.plusDays(70).toString()))
            .andExpect(jsonPath("$.cabe.simulada").value(false))
            .andExpect(jsonPath("$.cabe.nuncaVistos").value(3));
        assertThat(jdbc.queryForObject("SELECT ativo FROM concurso WHERE id = ?", Boolean.class, concurso)).isFalse();
    }

    @Test
    void d139_dataERitmoSimulados_ficamDeForaOsUltimosPorPesoEOrdem_enadaEGravado() throws Exception {
        var sessoesAntes = jdbc.queryForObject("SELECT count(*) FROM sessao", Integer.class);
        mockMvc.perform(get("/api/concursos/{id}/diagnostico", concurso)
                .param("data", hoje.plusDays(14).toString()).param("ritmo", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cabe.simulada").value(true))
            .andExpect(jsonPath("$.cabe.ritmoMedido").value(false))
            .andExpect(jsonPath("$.cabe.cabem").value(2))
            .andExpect(jsonPath("$.cabe.ficamDeFora.length()").value(1))
            .andExpect(jsonPath("$.cabe.ficamDeFora[0].assuntoId").value(nuncaBaixo))
            .andExpect(jsonPath("$.cabe.todosVistosEm").value(hoje.plusDays(21).toString()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessao", Integer.class)).isEqualTo(sessoesAntes);
        assertThat(jdbc.queryForObject("SELECT data_prova FROM concurso WHERE id = ?", LocalDate.class, concurso)).isEqualTo(hoje.plusDays(70));
    }

    @Test
    void d139_semDataFutura_naoHaOQueCabe_eDataPassadaERecusada() throws Exception {
        jdbc.update("UPDATE concurso SET data_prova = ? WHERE id = ?", hoje.minusDays(5), concurso);
        mockMvc.perform(get("/api/concursos/{id}/diagnostico", concurso))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cabe").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.ritmoMedido").exists());
        mockMvc.perform(get("/api/concursos/{id}/diagnostico", concurso).param("data", hoje.minusDays(1).toString()))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/concursos/{id}/diagnostico", 999_999_999L)).andExpect(status().isNotFound());
    }
}
