package br.com.estudos.simulado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.regua.ReguaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.exception.ValidationException;

/** D-148 e D-149 (docs/SPRINT-34-REGISTRO-SIMULADO.md §4). Dentro da transação, os concursos de outros testes são desativados. */
@Transactional
class SimuladoBrancosTest extends IntegracaoTestBase {

    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired SimuladoService simulados;
    @Autowired ReguaService regua;
    @Autowired ResultadoSimuladoRepository resultados;
    @Autowired br.com.estudos.assunto.AssuntoService assuntos;

    long disciplina;
    LocalDate dia = LocalDate.of(2026, 3, 3);

    /** Um alvo com errada anula certa e a disciplina valendo 1 ponto por questão. */
    @BeforeEach
    void cenario() {
        jdbc.update("UPDATE concurso SET ativo = false WHERE ativo");
        var n = System.nanoTime();
        disciplina = disciplinas.criar(new DisciplinaRequest("Simulado " + n, TipoPeso.MEDIO)).getId();
        var concurso = jdbc.queryForObject("""
            INSERT INTO concurso (nome, origem, slug, ativo, ativado_em, errada_anula_certa) VALUES ('S', 'PLANEJAMENTO', 's-' || ?, true, now(), true)
            RETURNING id""", Long.class, n);
        var assunto = assuntos.criar(new br.com.estudos.assunto.AssuntoRequest(disciplina, "Assunto " + n, TipoPeso.MEDIO, (short) 3, 1, null)).getId();
        jdbc.update("INSERT INTO concurso_assunto (concurso_id, assunto_id, disciplina_id, peso, ordem) VALUES (?, ?, ?, 'MEDIO', 1)",
            concurso, assunto, disciplina);
    }

    SimuladoRegistrado registrar(int certas, int total, Integer brancos) {
        return simulados.registrar(new SimuladoRequest(dia, 180,
            List.of(new ResultadoSimuladoRequest(disciplina, FormatoBanca.CERTO_ERRADO, certas, total, brancos))));
    }

    @Test
    void d148_brancosAcimaDoTotal_recusadoNoServicoENoBanco() throws Exception {
        assertThatThrownBy(() -> registrar(10, 20, 11)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> registrar(10, 20, -1)).isInstanceOf(ValidationException.class);
        mockMvc.perform(post("/api/simulados").contentType(MediaType.APPLICATION_JSON).content("""
                {"data": "2026-03-03", "duracaoMinutos": 180, "resultados": [
                  {"disciplinaId": %d, "formato": "CERTO_ERRADO", "questoesCorretas": 10, "questoesTotal": 20, "questoesEmBranco": 11}]}"""
                .formatted(disciplina)))
            .andExpect(status().is4xxClientError());
        var s = registrar(10, 20, null);
        assertThatThrownBy(() -> jdbc.update("UPDATE resultado_simulado SET questoes_em_branco = 11 WHERE simulado_id = ?", s.simulado().getId()))
            .hasMessageContaining("ck_resultado_simulado_d148_brancos");
    }

    @Test
    void d148_pontosDescontamSoAsErradas_eAM1IgnoraOsBrancos() {
        var com = registrar(10, 20, 4).simulado().getId(); // 6 erradas → 10 − 6 = 4
        var sem = registrar(10, 20, null).simulado().getId(); // 10 erradas → 0
        var pontos = regua.pontosDosSimulados();
        assertThat(pontos.stream().filter(p -> p.simuladoId() == com).findFirst().orElseThrow().nota().pontos()).isEqualByComparingTo("4");
        assertThat(pontos.stream().filter(p -> p.simuladoId() == sem).findFirst().orElseThrow().nota().pontos()).isEqualByComparingTo("0");
        var m1 = resultados.listarAcerto(dia, dia).stream().filter(l -> l.getDisciplinaId() == disciplina).toList();
        assertThat(m1).hasSize(2).allSatisfy(l -> {
            assertThat(l.getQuestoesCorretas()).isEqualTo(10);
            assertThat(l.getQuestoesTotal()).isEqualTo(20);
        });
    }

    @Test
    void d149_arquivado_saiDaM1EDosPontos_semSerApagado() throws Exception {
        var id = registrar(12, 20, 2).simulado().getId();
        mockMvc.perform(post("/api/simulados/{id}/arquivar", id)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/simulados/{id}/arquivar", id)).andExpect(status().isNoContent()); // idempotente
        mockMvc.perform(post("/api/simulados/{id}/arquivar", 999_999_999L)).andExpect(status().isNotFound());
        assertThat(resultados.listarAcerto(dia, dia)).noneMatch(l -> l.getDisciplinaId() == disciplina);
        assertThat(regua.pontosDosSimulados()).noneMatch(p -> p.simuladoId() == id);
        mockMvc.perform(get("/api/simulados"))
            .andExpect(jsonPath("$[?(@.id == " + id + ")].arquivadoEm").isNotEmpty())
            .andExpect(jsonPath("$[?(@.id == " + id + ")].resultados[0].questoesEmBranco").value(2));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM resultado_simulado WHERE simulado_id = ?", Integer.class, id)).isEqualTo(1);
    }
}
