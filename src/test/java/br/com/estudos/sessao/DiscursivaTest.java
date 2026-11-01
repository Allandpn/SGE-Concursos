package br.com.estudos.sessao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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
import br.com.estudos.metrica.MetricaService;
import br.com.estudos.regua.ReguaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.shared.exception.ValidationException;

/** D-133 e D-134 (docs/SPRINT-23-REGISTRO-E-PROVA-REAL.md §4). */
class DiscursivaTest extends IntegracaoTestBase {

    @Autowired SessaoService sessoes;
    @Autowired MetricaService metricas;
    @Autowired ReguaService regua;
    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;

    long assunto;

    @BeforeEach
    void assunto() {
        var d = disciplinas.criar(new DisciplinaRequest("Disciplina Discursiva " + System.nanoTime(), TipoPeso.MEDIO));
        assunto = assuntos.criar(new AssuntoRequest(d.getId(), "Improbidade " + System.nanoTime(), TipoPeso.MEDIO, (short) 3, 1, null)).getId();
    }

    SessaoRequest discursiva(Integer nota, LocalDate data) {
        return new SessaoRequest(assunto, TipoSessao.DISCURSIVA, data, 40, null, null, null, null, ResultadoSessao.PARCIAL, null,
            UUID.randomUUID(), null, null, null, nota);
    }

    @Test
    void d134_aNotaDaOResultadoPelosLimiares() {
        assertThat(sessoes.registrar(discursiva(72, LocalDate.now())).getResultado()).isEqualTo(ResultadoSessao.SUCESSO);
        assertThat(sessoes.registrar(discursiva(70, LocalDate.now())).getResultado()).isEqualTo(ResultadoSessao.SUCESSO);
        assertThat(sessoes.registrar(discursiva(55, LocalDate.now())).getResultado()).isEqualTo(ResultadoSessao.PARCIAL);
        assertThat(sessoes.registrar(discursiva(39, LocalDate.now())).getResultado()).isEqualTo(ResultadoSessao.FALHA);
    }

    @Test
    void d134_notaForaDaFaixaOuAusente_eRecusada_noServicoENoBanco() {
        assertThatThrownBy(() -> sessoes.registrar(discursiva(101, LocalDate.now()))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> sessoes.registrar(discursiva(null, LocalDate.now()))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, previsao_reconstrucao, resultado, tentativa_id, ativo)
            VALUES (?, 'DISCURSIVA', current_date, 10, 'SUCESSO', 'SUCESSO', ?, true)""", assunto, UUID.randomUUID()))
            .hasMessageContaining("ck_sessao_d134_discursiva");
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, tentativa_id, ativo, nota_discursiva)
            VALUES (?, 'ESTUDO', current_date, 10, ?, true, 50)""", assunto, UUID.randomUUID()))
            .hasMessageContaining("ck_sessao_d134_discursiva");
    }

    @Test
    void d134_cumpreARevisao_eEntraNaM3Subjetiva() throws Exception {
        sessoes.registrar(new SessaoRequest(assunto, TipoSessao.ESTUDO, LocalDate.now().minusDays(3), 50, null, null, null, null, null, null,
            UUID.randomUUID(), null));
        var antes = metricas.m3();
        var s = sessoes.registrar(discursiva(30, LocalDate.now()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revisao WHERE assunto_id = ? AND sessao_cumpriu_id = ?", Integer.class, assunto, s.getId()))
            .isEqualTo(1);
        var depois = metricas.m3();
        assertThat(depois.subjetiva().toString()).isNotEqualTo(antes.subjetiva().toString());
        mockMvc.perform(get("/api/turno/plano")).andExpect(jsonPath("$.reestudos[?(@.assuntoId == " + assunto + ")]").exists());
    }

    @Test
    void d133_formatoDoLoteRapido_vemDoAssunto() throws Exception {
        sessoes.registrar(new SessaoRequest(assunto, TipoSessao.QUESTOES, LocalDate.now(), 20, 7, 10,
            br.com.estudos.shared.enums.FormatoBanca.CERTO_ERRADO, (short) 70, null, null, UUID.randomUUID(), null));
        assertThat(regua.formatoSugerido(assunto)).isEqualTo("CERTO_ERRADO");
        mockMvc.perform(get("/api/assuntos/{id}/lote-sugerido", assunto)).andExpect(jsonPath("$.formato").value("CERTO_ERRADO"));
    }
}
