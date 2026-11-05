package br.com.estudos.leitura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.leitura.MarcacaoService.Mudanca;
import br.com.estudos.leitura.MarcacaoService.Tipo;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** D-172 (docs/SPRINT-44-ICONES-E-PONTOS-ALTOS.md): os pontos altos do assunto para a recuperação depois de conferir. */
class PontosAltosTest extends IntegracaoTestBase {

    static final Instant T = Instant.parse("2026-11-05T21:00:00Z");

    @Autowired MarcacaoService marcacoes;
    @Autowired PontosAltosService pontosAltos;
    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;

    long assunto;
    long bloco1;
    long bloco2;

    @BeforeEach
    void assunto() {
        var d = disciplinas.criar(new DisciplinaRequest("Disciplina Pontos " + System.nanoTime(), TipoPeso.MEDIO));
        assunto = assuntos.criar(new AssuntoRequest(d.getId(), "Atos " + System.nanoTime(), TipoPeso.MEDIO, (short) 3, 1, null)).getId();
        bloco1 = segmento(1);
        bloco2 = segmento(2);
    }

    long segmento(int ordem) {
        return jdbc.queryForObject("""
            INSERT INTO segmento (assunto_id, chave_externa, ordem, arquivo, pagina_inicial, pagina_final) VALUES (?, ?, ?, ?, 1, 24) RETURNING id""",
            Long.class, assunto, "seg-pontos-" + System.nanoTime(), ordem, "/material/blocos/x/segmento-0" + ordem + ".pdf");
    }

    UUID destaque(long segmento, int pagina, double y, String cor, String citacao) {
        var chave = UUID.randomUUID();
        marcacoes.aplicar(List.of(new Mudanca(UUID.randomUUID(), chave, segmento, pagina, Tipo.DESTAQUE, null, List.of(List.of(0.1, y, 0.5, 0.02)),
            citacao, null, null, cor, null, false, T)));
        return chave;
    }

    @Test
    void d172_destaquesComNotaENotasDePagina_naOrdemDoBlocoPaginaEAltura_semRemovidasNemArquivados() {
        // fora de ordem de propósito: bloco 2 primeiro, página 15 depois da 17, o de baixo antes do de cima
        destaque(bloco2, 3, 0.5, "DUVIDA", "convalidação de vício de forma");
        var baixo = destaque(bloco1, 17, 0.8, "IMPORTANTE", "o ato nulo não gera direito adquirido");
        destaque(bloco1, 17, 0.1, "DECORAR", "PATI");
        var comNota = destaque(bloco1, 15, 0.3, "DECORAR", "presunção de legitimidade");
        marcacoes.aplicar(List.of(new Mudanca(UUID.randomUUID(), UUID.randomUUID(), bloco1, 15, Tipo.NOTA_TRECHO, comNota, null, null, null, null,
            null, "cai muito na FGV", false, T)));
        marcacoes.aplicar(List.of(new Mudanca(UUID.randomUUID(), UUID.randomUUID(), bloco1, 16, Tipo.NOTA_PAGINA, null, null, null, 0.4, 0.5,
            null, "rever o quadro", false, T)));
        // removida não aparece
        var removida = destaque(bloco1, 18, 0.2, "IMPORTANTE", "isto saiu");
        marcacoes.aplicar(List.of(new Mudanca(UUID.randomUUID(), removida, null, null, null, null, null, null, null, null, "IMPORTANTE", null, true,
            T.plusSeconds(60))));
        // bloco arquivado também não
        long antigo = segmento(9);
        destaque(antigo, 1, 0.1, "IMPORTANTE", "material antigo");
        jdbc.update("UPDATE segmento SET arquivado_em = now() WHERE id = ?", antigo);

        var p = pontosAltos.doAssunto(assunto);
        assertThat(p).extracting(PontosAltosService.PontoAlto::citacao)
            .containsExactly("presunção de legitimidade", null, "PATI", "o ato nulo não gera direito adquirido", "convalidação de vício de forma");
        assertThat(p.get(0).nota()).isEqualTo("cai muito na FGV");
        assertThat(p.get(0).cor()).isEqualTo("DECORAR");
        assertThat(p.get(1).tipo()).isEqualTo("NOTA_PAGINA");
        assertThat(p.get(1).nota()).isEqualTo("rever o quadro");
        assertThat(p.get(1).cor()).isNull();
        assertThat(p.get(3).chave()).isEqualTo(baixo);
        assertThat(p.get(3).nota()).isNull();
        assertThat(p.get(4).bloco()).isEqualTo(2);
        assertThat(p.get(4).segmentoId()).isEqualTo(bloco2);
    }

    @Test
    void d172_aRota_vazioSemMarcacao_e404ParaAssuntoQueNaoExiste() throws Exception {
        mockMvc.perform(get("/api/assuntos/{id}/pontos-altos", assunto)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        destaque(bloco1, 7, 0.2, "IMPORTANTE", "retoma ou antecipa");
        mockMvc.perform(get("/api/assuntos/{id}/pontos-altos", assunto)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].citacao").value("retoma ou antecipa"))
            .andExpect(jsonPath("$[0].pagina").value(7))
            .andExpect(jsonPath("$[0].bloco").value(1))
            .andExpect(jsonPath("$[0].cor").value("IMPORTANTE"));
        mockMvc.perform(get("/api/assuntos/{id}/pontos-altos", 999_999_999L)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("ASSUNTO_INEXISTENTE"));
    }
}
