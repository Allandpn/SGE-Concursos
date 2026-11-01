package br.com.estudos.retafinal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.revisao.EstadoConsolidacao;
import br.com.estudos.frente.FrenteService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.turno.TurnoService;

/**
 * A reta final (D-140 a D-144; docs/SPRINT-21-RETA-FINAL.md §4). "Hoje" congelado num sábado (03_INVARIANTES §10); dentro de uma
 * transação, as disciplinas e concursos de outros testes são arquivados e desativados para o cenário ser só o daqui.
 */
@Transactional
class RetaFinalTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.of(2026, 6, 13); // sábado

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(HOJE.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired DisciplinaRepository disciplinaRepository;
    @Autowired AssuntoService assuntos;
    @Autowired RetaFinalService retaFinal;
    @Autowired RetaFinalConsulta consulta;
    @Autowired FrenteService frente;
    @Autowired TurnoService turno;

    long concurso;
    long disciplina;
    int ordem;

    @BeforeEach
    void cenario() {
        disciplinaRepository.findByAtivoTrue().forEach(d -> disciplinas.arquivar(d.getId()));
        jdbc.update("UPDATE concurso SET ativo = false WHERE ativo");
        jdbc.update("DELETE FROM resultado_simulado");
        jdbc.update("DELETE FROM simulado");
        disciplina = disciplinas.criar(new DisciplinaRequest("Reta " + System.nanoTime(), TipoPeso.MEDIO)).getId();
        concurso = jdbc.queryForObject("""
            INSERT INTO concurso (nome, origem, slug, data_prova, ativo, ativado_em)
            VALUES ('Reta', 'PLANEJAMENTO', 'reta-' || ?, ?, true, now() - interval '1 day') RETURNING id""",
            Long.class, System.nanoTime(), HOJE.plusDays(50));
    }

    long assunto(TipoPeso peso) {
        return assuntos.criar(new AssuntoRequest(disciplina, "Assunto " + System.nanoTime(), peso, (short) 3, ++ordem, null)).getId();
    }

    /** Assunto consolidado (dois SUCESSO no nível-alvo) com a manutenção pendente em {@code manutencao}. */
    long consolidado(TipoPeso peso, LocalDate manutencao) {
        var a = assunto(peso);
        jdbc.update("INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, tentativa_id, ativo) VALUES (?, 'ESTUDO', ?, 50, ?, true)",
            a, HOJE.minusDays(200), UUID.randomUUID());
        var alvo = EstadoConsolidacao.nivelAlvo(peso);
        for (var i = 0; i < 2; i++) {
            var s = jdbc.queryForObject("""
                INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, previsao_reconstrucao, resultado, tentativa_id, ativo)
                VALUES (?, 'RECUPERACAO', ?, 8, 'SUCESSO', 'SUCESSO', ?, true) RETURNING id""", Long.class, a, HOJE.minusDays(100 - i), UUID.randomUUID());
            jdbc.update("INSERT INTO revisao (assunto_id, nivel, data_prevista, sessao_cumpriu_id, situacao) VALUES (?, ?, ?, ?, 'CUMPRIDA')",
                a, alvo, HOJE.minusDays(100 - i), s);
        }
        jdbc.update("INSERT INTO revisao (assunto_id, nivel, data_prevista, situacao) VALUES (?, ?, ?, 'PENDENTE')", a, alvo, manutencao);
        return a;
    }

    LocalDate dataPrevista(long assunto) {
        return jdbc.queryForObject("SELECT data_prevista FROM revisao WHERE assunto_id = ? AND situacao = 'PENDENTE'", LocalDate.class, assunto);
    }

    @Test
    void d140_sugere_aAteNSemanas_eAgoraNaoCala() throws Exception {
        mockMvc.perform(get("/api/reta-final"))
            .andExpect(jsonPath("$.sugestao.concursoId").value(concurso))
            .andExpect(jsonPath("$.sugestao.semanas").value(8))
            .andExpect(jsonPath("$.ativa").value(Matchers.nullValue()));
        mockMvc.perform(post("/api/concursos/{id}/reta-final/dispensar", concurso)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/reta-final")).andExpect(jsonPath("$.sugestao").value(Matchers.nullValue()));
        // Longe da prova: sem sugestão, mas dá para entrar pelo botão.
        jdbc.update("UPDATE concurso SET data_prova = ?, reta_final_dispensada_em = NULL WHERE id = ?", HOJE.plusDays(100), concurso);
        assertThat(retaFinal.situacao().sugestao()).isNull();
        assertThat(retaFinal.entrar(concurso)).isNotNull();
        assertThat(consulta.ativa()).isPresent();
    }

    @Test
    void d140_semDataFutura_ouForaDoAlvo_naoEntra() {
        jdbc.update("UPDATE concurso SET data_prova = ? WHERE id = ?", HOJE.minusDays(1), concurso);
        assertThat(retaFinal.situacao().sugestao()).isNull();
        assertThatThrownBy(() -> retaFinal.entrar(concurso)).hasMessageContaining("data de prova");
        jdbc.update("UPDATE concurso SET data_prova = ?, ativo = false WHERE id = ?", HOJE.plusDays(30), concurso);
        assertThatThrownBy(() -> retaFinal.entrar(concurso)).hasMessageContaining("concurso-alvo");
    }

    @Test
    void d141_ativaDerivada_terminaSozinha_eUmaAbertaPorConcurso() throws Exception {
        mockMvc.perform(post("/api/concursos/{id}/reta-final", concurso)).andExpect(status().isOk());
        assertThat(consulta.ativa()).get().extracting(RetaFinalConsulta.Ativa::dias).isEqualTo(50L);
        assertThat(retaFinal.situacao().sugestao()).isNull();

        // Trocou de alvo (a ativação ficou depois da entrada): inativa sem nada escrito; entrar de novo fecha a velha.
        jdbc.update("UPDATE concurso SET ativado_em = now() + interval '1 minute' WHERE id = ?", concurso);
        assertThat(consulta.ativa()).isEmpty();
        jdbc.update("UPDATE concurso SET ativado_em = now() - interval '1 day' WHERE id = ?", concurso);

        // A prova passou: inativa.
        jdbc.update("UPDATE concurso SET data_prova = ? WHERE id = ?", HOJE.minusDays(1), concurso);
        assertThat(consulta.ativa()).isEmpty();
        jdbc.update("UPDATE concurso SET data_prova = ? WHERE id = ?", HOJE.plusDays(50), concurso);

        mockMvc.perform(post("/api/concursos/{id}/reta-final/sair", concurso)).andExpect(status().isNoContent());
        assertThat(consulta.ativa()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reta_final WHERE concurso_id = ?", Integer.class, concurso)).isEqualTo(1);

        // Entrar de novo abre outra (nada se apaga); a segunda aberta é recusada pelo banco. Por último: o erro aborta a transação.
        retaFinal.entrar(concurso);
        assertThat(consulta.ativa()).isPresent();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO reta_final (concurso_id) VALUES (?)", concurso))
            .hasMessageContaining("ux_reta_final_d141_uma_aberta");
    }

    @Test
    void d142_assuntoNovoSoDePesoAlto() {
        var medio = assunto(TipoPeso.MEDIO); // ordem 1
        var alto = assunto(TipoPeso.ALTO); // ordem 2
        assertThat(frente.proximaSugestaoDeConteudo()).get().extracting(a -> a.getId()).isEqualTo(medio);
        retaFinal.entrar(concurso);
        assertThat(frente.proximaSugestaoDeConteudo()).get().extracting(a -> a.getId()).isEqualTo(alto);
    }

    @Test
    void d143_manutencaoDeMedioEBaixoForaDaFila_eAltoAntecipadoAteProvaMenos7() {
        var medio = consolidado(TipoPeso.MEDIO, HOJE.minusDays(3));
        var baixo = consolidado(TipoPeso.BAIXO, HOJE.minusDays(2));
        var altoDepois1 = consolidado(TipoPeso.ALTO, HOJE.plusDays(60));
        var altoDepois2 = consolidado(TipoPeso.ALTO, HOJE.plusDays(80));
        var altoAntes = consolidado(TipoPeso.ALTO, HOJE.plusDays(20));
        assertThat(turno.plano().filaRecuperacao()).extracting(i -> i.assuntoId()).contains(medio, baixo);

        var entrada = retaFinal.entrar(concurso);

        assertThat(entrada.antecipadas()).isEqualTo(2);
        assertThat(entrada.ate()).isEqualTo(HOJE.plusDays(43));
        var plano = turno.plano();
        assertThat(plano.filaRecuperacao()).extracting(i -> i.assuntoId()).doesNotContain(medio, baixo);
        assertThat(plano.manutencoesEsperando()).isEqualTo(2);
        assertThat(plano.retaFinal().concursoId()).isEqualTo(concurso);
        // 43 dias (amanhã até prova − 7), 2 revisões: amanhã + 0 e amanhã + 21.
        assertThat(dataPrevista(altoDepois1)).isEqualTo(HOJE.plusDays(1));
        assertThat(dataPrevista(altoDepois2)).isEqualTo(HOJE.plusDays(22));
        assertThat(jdbc.queryForObject("SELECT data_prevista_antes_reta_final FROM revisao WHERE assunto_id = ? AND situacao = 'PENDENTE'",
            LocalDate.class, altoDepois2)).isEqualTo(HOJE.plusDays(80));
        assertThat(dataPrevista(altoAntes)).isEqualTo(HOJE.plusDays(20));

        retaFinal.sair(concurso);
        assertThat(turno.plano().filaRecuperacao()).extracting(i -> i.assuntoId()).contains(medio, baixo);
        assertThat(dataPrevista(medio)).isEqualTo(HOJE.minusDays(3));
        assertThat(dataPrevista(altoDepois1)).isEqualTo(HOJE.plusDays(1)); // sair não desfaz (ADR-056)
    }

    @Test
    void d144_simuladoDaSemanaNoFimDeSemana() {
        assertThat(turno.plano().simuladoDaSemana()).isFalse(); // fora da reta final
        retaFinal.entrar(concurso);
        assertThat(turno.plano().simuladoDaSemana()).isTrue();
        assertThat(turno.plano().proximoBloco()).isNull();
        jdbc.update("INSERT INTO simulado (data, duracao_minutos) VALUES (?, 180)", HOJE.minusDays(5)); // a segunda desta semana
        assertThat(turno.plano().simuladoDaSemana()).isFalse();
        assertThat(TurnoService.diaDoSimulado(HOJE.plusDays(1))).isTrue(); // domingo
        assertThat(TurnoService.diaDoSimulado(HOJE.plusDays(3))).isFalse(); // terça
    }
}
