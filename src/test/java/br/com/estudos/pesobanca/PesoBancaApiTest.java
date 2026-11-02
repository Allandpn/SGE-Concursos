package br.com.estudos.pesobanca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.concurso.ConcursoRepository;
import br.com.estudos.concurso.ConcursoService;
import br.com.estudos.concurso.ItemCobertura;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * D-145 e D-147 pela rota (docs/SPRINT-22-PESO-PELA-BANCA.md §4). Ano congelado em 2026; dentro da transação, os concursos de
 * outros testes são desativados.
 */
@Transactional
class PesoBancaApiTest extends IntegracaoTestBase {

    @TestConfiguration
    static class RelogioFixoConfig {
        @Bean
        @Primary
        Clock clockFixo() {
            return Clock.fixed(LocalDate.of(2026, 6, 15).atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired DisciplinaRepository disciplinaRepository;
    @Autowired AssuntoService assuntos;
    @Autowired AssuntoRepository assuntoRepository;
    @Autowired ConcursoRepository concursoRepository;
    @Autowired ConcursoService concursoService;
    @Autowired PesoBancaService pesoBanca;

    long concurso;
    long disciplina;
    long s1, s2, s3, s4;
    long p1, p2;
    int numero;

    /**
     * Disciplina com quatro assuntos (edital: médio, médio, baixo, alto). Contam: P1 (CESPE, 2025, deste concurso) e P2 (cebraspe, sem
     * ano, de outro concurso). Não contam: P3 (FGV), P4 (CESPE de 2015) e as anuladas. s1 20, s2 8, s3 4, s4 0 → 32 questões.
     */
    @BeforeEach
    void cenario() {
        jdbc.update("UPDATE concurso SET ativo = false WHERE ativo");
        var n = System.nanoTime();
        disciplina = disciplinas.criar(new DisciplinaRequest("Banca " + n, TipoPeso.MEDIO)).getId();
        s1 = assunto("SQL " + n, TipoPeso.MEDIO, 1);
        s2 = assunto("Normalização " + n, TipoPeso.MEDIO, 2);
        s3 = assunto("Índices " + n, TipoPeso.BAIXO, 3);
        s4 = assunto("NoSQL " + n, TipoPeso.ALTO, 4);
        concurso = jdbc.queryForObject("""
            INSERT INTO concurso (nome, origem, slug, banca, ativo, ativado_em) VALUES ('Banca', 'PLANEJAMENTO', 'banca-' || ?, 'Cebraspe (CESPE)', true, now())
            RETURNING id""", Long.class, n);
        var outro = jdbc.queryForObject("INSERT INTO concurso (nome, origem, slug) VALUES ('Outro', 'PLANEJAMENTO', 'outro-' || ?) RETURNING id",
            Long.class, n);
        int ordem = 0;
        for (var a : List.of(s1, s2, s3, s4)) {
            var peso = jdbc.queryForObject("SELECT peso FROM assunto WHERE id = ?", String.class, a);
            jdbc.update("INSERT INTO concurso_assunto (concurso_id, assunto_id, disciplina_id, peso, ordem) VALUES (?, ?, ?, ?, ?)",
                concurso, a, disciplina, peso, ++ordem);
        }
        p1 = prova(concurso, "CESPE", 2025);
        p2 = prova(outro, "cebraspe", null);
        var p3 = prova(outro, "FGV", 2025);
        var p4 = prova(outro, "CESPE", 2015);
        questoes(p1, s1, 10); questoes(p2, s1, 10);
        questoes(p1, s2, 4); questoes(p2, s2, 4);
        questoes(p1, s3, 2); questoes(p2, s3, 2);
        questoes(p3, s4, 10); questoes(p4, s4, 10);
        for (var i = 0; i < 5; i++) {
            jdbc.update("INSERT INTO questao (prova_id, numero, enunciado, motivo_nao_avaliavel, assunto_id, atribuicao) VALUES (?, ?, 'x', 'ANULADA', ?, 'CONFIRMADA')",
                p1, ++numero, s4);
        }
    }

    long assunto(String nome, TipoPeso peso, int ordem) {
        return assuntos.criar(new AssuntoRequest(disciplina, nome, peso, (short) 3, ordem, null)).getId();
    }

    long prova(long concursoId, String banca, Integer ano) {
        return jdbc.queryForObject("""
            INSERT INTO prova (concurso_id, nome, banca, ano, status, hash_prova) VALUES (?, 'p', ?, ?, 'LENDO', ?) RETURNING id""",
            Long.class, concursoId, banca, ano, "h" + System.nanoTime());
    }

    void questoes(long prova, long assunto, int quantas) {
        for (var i = 0; i < quantas; i++) {
            jdbc.update("INSERT INTO questao (prova_id, numero, enunciado, resposta_gabarito, assunto_id, atribuicao) VALUES (?, ?, 'x', 'A', ?, 'CONFIRMADA')",
                prova, ++numero, assunto);
        }
    }

    String peso(long assunto) {
        return jdbc.queryForObject("SELECT peso FROM assunto WHERE id = ?", String.class, assunto);
    }

    @Test
    void d145_d146_contaSoAMesmaBancaRecenteEAvaliavel_eProposUmDegrau() throws Exception {
        mockMvc.perform(get("/api/concursos/{id}/peso-banca", concurso))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provas").value(2))
            .andExpect(jsonPath("$.questoes").value(32))
            .andExpect(jsonPath("$.totalPropostas").value(3))
            .andExpect(jsonPath("$.disciplinas[0].suficiente").value(true))
            .andExpect(jsonPath("$.disciplinas[0].propostas[?(@.assuntoId == " + s1 + ")].proposto").value("ALTO"))
            .andExpect(jsonPath("$.disciplinas[0].propostas[?(@.assuntoId == " + s1 + ")].sobeParaAlto").value(true))
            .andExpect(jsonPath("$.disciplinas[0].propostas[?(@.assuntoId == " + s2 + ")].proposto").value("ALTO"))
            .andExpect(jsonPath("$.disciplinas[0].propostas[?(@.assuntoId == " + s4 + ")].proposto").value("MEDIO"))
            .andExpect(jsonPath("$.disciplinas[0].propostas[?(@.assuntoId == " + s4 + ")].questoes").value(0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM peso_banca", Integer.class)).isZero();
    }

    @Test
    void d145_semAmostraSuficiente_naoPropoe_eSemBancaNoConcursoUsaADaProva() {
        jdbc.update("UPDATE concurso SET banca = NULL WHERE id = ?", concurso);
        var s = pesoBanca.situacao(concurso);
        assertThat(PesoBanca.mesmaBanca(s.banca(), "CESPE")).isTrue(); // a da prova P1, deste concurso
        assertThat(s.totalPropostas()).isEqualTo(3);
        jdbc.update("DELETE FROM questao WHERE prova_id = ?", p2); // sobra uma prova
        var pouca = pesoBanca.situacao(concurso);
        assertThat(pouca.disciplinas().get(0).suficiente()).isFalse();
        assertThat(pouca.totalPropostas()).isZero();
    }

    @Test
    void d147_aceitar_reimportarMantem_provaNovaNaoMuda_desfazerVolta() throws Exception {
        mockMvc.perform(post("/api/concursos/{id}/peso-banca/aceitar", concurso).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assuntoIds\": [" + s1 + ", " + s4 + ", 999999999]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPropostas").value(1))
            .andExpect(jsonPath("$.ajustados.length()").value(2));
        assertThat(peso(s1)).isEqualTo("ALTO");
        assertThat(peso(s4)).isEqualTo("MEDIO");

        // Reimportar o edital (a cobertura é regravada com o peso do edital): o ajuste vale.
        var c = concursoRepository.findById(concurso).orElseThrow();
        var d = disciplinaRepository.findById(disciplina).orElseThrow();
        concursoService.ativarComCobertura(c, List.of(
            new ItemCobertura(assuntoRepository.findById(s1).orElseThrow(), d, TipoPeso.MEDIO, 1),
            new ItemCobertura(assuntoRepository.findById(s2).orElseThrow(), d, TipoPeso.MEDIO, 2),
            new ItemCobertura(assuntoRepository.findById(s3).orElseThrow(), d, TipoPeso.BAIXO, 3),
            new ItemCobertura(assuntoRepository.findById(s4).orElseThrow(), d, TipoPeso.ALTO, 4)));
        assuntoRepository.flush();
        assertThat(peso(s1)).isEqualTo("ALTO");
        assertThat(jdbc.queryForObject("SELECT peso FROM concurso_assunto WHERE concurso_id = ? AND assunto_id = ?", String.class, concurso, s1))
            .isEqualTo("MEDIO");

        // Prova nova muda a proposta, nunca o peso.
        var p5 = prova(concurso, "CESPE", 2026);
        questoes(p5, s4, 40);
        assertThat(peso(s4)).isEqualTo("MEDIO");
        assertThat(pesoBanca.situacao(concurso).ajustados()).anySatisfy(a -> {
            assertThat(a.assuntoId()).isEqualTo(s4);
            assertThat(a.propostaHoje()).isNotEqualTo(TipoPeso.MEDIO);
        });

        mockMvc.perform(post("/api/concursos/{id}/peso-banca/{a}/desfazer", concurso, s1)).andExpect(status().isOk());
        assertThat(peso(s1)).isEqualTo("MEDIO");

        // Por último (o erro aborta a transação): dois vigentes do mesmo assunto são recusados pelo banco.
        assertThatThrownBy(() -> jdbc.update("INSERT INTO peso_banca (concurso_id, assunto_id, peso) VALUES (?, ?, 'ALTO')", concurso, s4))
            .hasMessageContaining("ux_peso_banca_d147_um_vigente");
    }
}
