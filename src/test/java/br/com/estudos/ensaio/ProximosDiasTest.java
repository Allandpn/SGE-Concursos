package br.com.estudos.ensaio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

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
import br.com.estudos.retafinal.RetaFinalService;
import br.com.estudos.revisao.EstadoConsolidacao;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** D-157 (docs/SPRINT-38-PROXIMOS-DIAS-E-ENSAIO.md §4). "Hoje" congelado numa quarta; o cenário é só o daqui, dentro da transação. */
@Transactional
class ProximosDiasTest extends IntegracaoTestBase {

    static final LocalDate HOJE = LocalDate.of(2026, 6, 10); // quarta

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
    @Autowired ProximosDiasService proximos;
    @Autowired RetaFinalService retaFinal;

    long disciplina;
    int ordem;

    @BeforeEach
    void cenario() {
        disciplinaRepository.findByAtivoTrue().forEach(d -> disciplinas.arquivar(d.getId()));
        jdbc.update("UPDATE concurso SET ativo = false WHERE ativo");
        jdbc.update("DELETE FROM resultado_simulado");
        jdbc.update("DELETE FROM simulado");
        jdbc.update("UPDATE parametro SET valor = '2' WHERE chave = 'teto_diario_recuperacoes'");
        disciplina = disciplinas.criar(new DisciplinaRequest("Próximos " + System.nanoTime(), TipoPeso.MEDIO)).getId();
    }

    long assunto(TipoPeso peso) {
        return assuntos.criar(new AssuntoRequest(disciplina, "Assunto " + System.nanoTime(), peso, (short) 3, ++ordem, null)).getId();
    }

    long emEscada(LocalDate prevista, int nivel) {
        var a = assunto(TipoPeso.MEDIO);
        jdbc.update("INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, tentativa_id, ativo) VALUES (?, 'ESTUDO', ?, 50, ?, true)",
            a, HOJE.minusDays(10), UUID.randomUUID());
        jdbc.update("INSERT INTO revisao (assunto_id, nivel, data_prevista, situacao) VALUES (?, ?, ?, 'PENDENTE')", a, nivel, prevista);
        return a;
    }

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

    @Test
    void d157_porDiaDeAmanhaAMais7_contraOTeto_comAManutencaoMarcada() {
        emEscada(HOJE, 1); // hoje fica no turno, não aqui
        emEscada(HOJE.plusDays(1), 1);
        emEscada(HOJE.plusDays(1), 2);
        emEscada(HOJE.plusDays(1), 3);
        var manut = consolidado(TipoPeso.MEDIO, HOJE.plusDays(2));
        emEscada(HOJE.plusDays(7), 2);
        emEscada(HOJE.plusDays(8), 2); // além dos 7 dias

        var p = proximos.proximos();
        assertThat(p.teto()).isEqualTo(2);
        assertThat(p.dias()).hasSize(7);
        assertThat(p.dias().get(0).data()).isEqualTo(HOJE.plusDays(1));
        assertThat(p.dias().get(0).total()).isEqualTo(3);
        assertThat(p.dias().get(0).acimaDoTeto()).isTrue();
        assertThat(p.dias().get(1).revisoes()).singleElement().satisfies(r -> {
            assertThat(r.assuntoId()).isEqualTo(manut);
            assertThat(r.manutencao()).isTrue();
        });
        assertThat(p.dias().get(1).acimaDoTeto()).isFalse();
        assertThat(p.dias().get(6).total()).isEqualTo(1);
        assertThat(p.dias().stream().mapToInt(ProximosDiasService.Dia::total).sum()).isEqualTo(5);
        assertThat(p.dias()).noneMatch(ProximosDiasService.Dia::simulado);
        assertThat(p.esperandoProva()).isZero();
        assertThat(p.dataProva()).isNull();
    }

    @Test
    void d157_naRetaFinal_manutencaoDeMedioAParte_simuladoNoFimDeSemana_eODiaDaProva() {
        var concurso = jdbc.queryForObject("""
            INSERT INTO concurso (nome, origem, slug, data_prova, ativo, ativado_em)
            VALUES ('Reta', 'PLANEJAMENTO', 'reta-' || ?, ?, true, now() - interval '1 day') RETURNING id""",
            Long.class, System.nanoTime(), HOJE.plusDays(5));
        consolidado(TipoPeso.MEDIO, HOJE.plusDays(3)); // espera a prova
        emEscada(HOJE.plusDays(3), 2);
        retaFinal.entrar(concurso);

        var p = proximos.proximos();
        assertThat(p.esperandoProva()).isEqualTo(1);
        assertThat(p.dias().get(2).total()).isEqualTo(1);
        assertThat(p.dataProva()).isEqualTo(HOJE.plusDays(5));
        // sáb 13 e dom 14: simulado; seg 15: a prova
        assertThat(p.dias().get(2).simulado()).isTrue();
        assertThat(p.dias().get(3).simulado()).isTrue();
        assertThat(p.dias().get(4).prova()).isTrue();
        assertThat(p.dias().get(4).simulado()).isFalse();
        assertThat(p.dias().get(0).simulado()).isFalse();

        // Com simulado já feito nesta semana, o fim de semana não pede outro.
        jdbc.update("INSERT INTO simulado (data, duracao_minutos) VALUES (?, 180)", HOJE);
        assertThat(proximos.proximos().dias()).noneMatch(ProximosDiasService.Dia::simulado);
    }
}
