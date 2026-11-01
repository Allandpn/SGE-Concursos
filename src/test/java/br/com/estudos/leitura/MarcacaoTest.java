package br.com.estudos.leitura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.leitura.MarcacaoService.Mudanca;
import br.com.estudos.leitura.MarcacaoService.Tipo;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** D-115 a D-118 e D-121 (docs/SPRINT-31-LEITOR.md §6): o banco, o serviço e as rotas do leitor. */
class MarcacaoTest extends IntegracaoTestBase {

    static final Instant T = Instant.parse("2026-11-01T21:00:00Z");
    static final List<List<Double>> RET = List.of(List.of(0.1, 0.2, 0.5, 0.02));

    @Autowired MarcacaoService marcacoes;
    @Autowired LeituraService leitura;
    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;

    long assunto;
    long segmento;
    long segmento2;

    @BeforeEach
    void bloco() {
        jdbc.execute("TRUNCATE marcacao_versao, marcacao, segmento_leitura RESTART IDENTITY");
        var d = disciplinas.criar(new DisciplinaRequest("Disciplina Leitor " + System.nanoTime(), TipoPeso.MEDIO));
        assunto = assuntos.criar(new AssuntoRequest(d.getId(), "Interpretação " + System.nanoTime(), TipoPeso.MEDIO, (short) 3, 1, null)).getId();
        segmento = novoSegmento(1);
        segmento2 = novoSegmento(2);
    }

    long novoSegmento(int ordem) {
        return jdbc.queryForObject("""
            INSERT INTO segmento (assunto_id, chave_externa, ordem, arquivo, pagina_inicial, pagina_final) VALUES (?, ?, ?, ?, 1, 24) RETURNING id""",
            Long.class, assunto, "seg-leitor-" + System.nanoTime(), ordem, "/material/blocos/x/segmento-0" + ordem + ".pdf");
    }

    Mudanca destaque(UUID chave, String cor, Instant em) {
        return new Mudanca(UUID.randomUUID(), chave, segmento, 7, Tipo.DESTAQUE, null, RET, "retoma ou antecipa", null, null, cor, null, false, em);
    }

    Mudanca edicao(UUID chave, String cor, String texto, boolean removida, Instant em) {
        return new Mudanca(UUID.randomUUID(), chave, null, null, null, null, null, null, null, null, cor, texto, removida, em);
    }

    // ---------- D-115 / D-116 no banco ----------

    @Test
    void d115_aFormaDeCadaTipo_eONotaDeTrechoPrecisaDeDestaque() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO marcacao (chave, segmento_id, pagina, tipo, criada_em) VALUES (?, ?, 1, 'DESTAQUE', now())",
            UUID.randomUUID(), segmento)).hasMessageContaining("ck_marcacao_d115_forma");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO marcacao (chave, segmento_id, pagina, tipo, retangulos, citacao, posicao_x, posicao_y, criada_em) "
            + "VALUES (?, ?, 1, 'NOTA_PAGINA', '[[0,0,1,1]]', 'x', 0.5, 0.5, now())", UUID.randomUUID(), segmento)).hasMessageContaining("ck_marcacao_d115_forma");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO marcacao (chave, segmento_id, pagina, tipo, criada_em) VALUES (?, ?, 0, 'NOTA_PAGINA', now())",
            UUID.randomUUID(), segmento)).hasMessageContaining("d115");
        var outro = jdbc.queryForObject("INSERT INTO marcacao (chave, segmento_id, pagina, tipo, posicao_x, posicao_y, criada_em) "
            + "VALUES (?, ?, 3, 'NOTA_PAGINA', 0.5, 0.5, now()) RETURNING id", Long.class, UUID.randomUUID(), segmento);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO marcacao (chave, segmento_id, pagina, tipo, destaque_id, criada_em) VALUES (?, ?, 3, 'NOTA_TRECHO', ?, now())",
            UUID.randomUUID(), segmento, outro)).hasMessageContaining("D-115");
    }

    @Test
    void d116_soAsTresCores_destaqueSempreComCor_notaSemCor() {
        var chave = UUID.randomUUID();
        marcacoes.aplicar(List.of(destaque(chave, "IMPORTANTE", T)));
        long id = jdbc.queryForObject("SELECT id FROM marcacao WHERE chave = ?", Long.class, chave);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO marcacao_versao (marcacao_id, mudanca_id, cor, editada_em, recebida_em) VALUES (?, ?, 'AZUL', now(), now())",
            id, UUID.randomUUID())).hasMessageContaining("ck_marcacao_versao_d116_cor");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO marcacao_versao (marcacao_id, mudanca_id, editada_em, recebida_em) VALUES (?, ?, now(), now())",
            id, UUID.randomUUID())).hasMessageContaining("D-116");
        var r = marcacoes.aplicar(List.of(destaque(UUID.randomUUID(), "AZUL", T), destaque(UUID.randomUUID(), null, T)));
        assertThat(r.recusadas()).hasSize(2).allMatch(x -> x.codigo().equals(MarcacaoService.INVALIDA));
    }

    // ---------- D-117 / D-118 ----------

    @Test
    void d118_aMesmaMudancaGravaUmaVez() {
        var m = destaque(UUID.randomUUID(), "DUVIDA", T);
        assertThat(marcacoes.aplicar(List.of(m)).aplicadas()).containsExactly(m.mudancaId());
        var r = marcacoes.aplicar(List.of(m, m));
        assertThat(r.repetidas()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marcacao_versao", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO marcacao_versao (marcacao_id, mudanca_id, cor, editada_em, recebida_em) "
            + "SELECT marcacao_id, mudanca_id, cor, now(), now() FROM marcacao_versao")).hasMessageContaining("uk_marcacao_versao_d118_mudanca");
    }

    @Test
    void d117_vigenteEAEdicaoMaisRecenteNoAparelho_chegueEmQueOrdemChegar() {
        var chave = UUID.randomUUID();
        marcacoes.aplicar(List.of(destaque(chave, "IMPORTANTE", T)));
        // Celular editou às 21:40 e mandou primeiro; o tablet editou às 21:12 sem rede e só mandou depois.
        marcacoes.aplicar(List.of(edicao(chave, "IMPORTANTE", "do celular", false, T.plusSeconds(40 * 60))));
        marcacoes.aplicar(List.of(edicao(chave, "DUVIDA", "do tablet", false, T.plusSeconds(12 * 60))));
        var v = marcacoes.vigentes(segmento);
        assertThat(v).hasSize(1);
        assertThat(v.get(0).texto()).isEqualTo("do celular");
        assertThat(v.get(0).cor()).isEqualTo("IMPORTANTE");
        var h = marcacoes.historico(segmento);
        assertThat(h.versoes()).hasSize(3).filteredOn(MarcacaoService.Versao::vigente).hasSize(1);
        assertThat(h.versoes()).anyMatch(x -> "do tablet".equals(x.texto()) && !x.vigente());
    }

    @Test
    void d117_removerSoTiraDaVista_eLevaANotaDoDestaque() {
        var chave = UUID.randomUUID();
        var nota = UUID.randomUUID();
        marcacoes.aplicar(List.of(destaque(chave, "IMPORTANTE", T),
            new Mudanca(UUID.randomUUID(), nota, segmento, 7, Tipo.NOTA_TRECHO, chave, null, null, null, null, null, "pronomes", false, T)));
        assertThat(marcacoes.vigentes(segmento)).hasSize(2);
        marcacoes.aplicar(List.of(edicao(chave, "IMPORTANTE", null, true, T.plusSeconds(60))));
        assertThat(marcacoes.vigentes(segmento)).isEmpty();
        assertThat(marcacoes.historico(segmento).removidas()).extracting(MarcacaoService.Marcacao::chave).containsExactly(chave);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM marcacao", Integer.class)).isEqualTo(2);
    }

    @Test
    void edicaoDeMarcacaoQueAindaNaoChegou_ficaParaDepois() {
        var chave = UUID.randomUUID();
        var editar = edicao(chave, "DECORAR", null, false, T.plusSeconds(60));
        var r = marcacoes.aplicar(List.of(editar));
        assertThat(r.recusadas()).extracting(MarcacaoService.Recusa::codigo).containsExactly(MarcacaoService.DESCONHECIDA);
        // A fila do aparelho manda de novo, agora com a criação na frente.
        r = marcacoes.aplicar(List.of(destaque(chave, "IMPORTANTE", T), editar));
        assertThat(r.aplicadas()).hasSize(2);
        assertThat(marcacoes.vigentes(segmento).get(0).cor()).isEqualTo("DECORAR");
    }

    @Test
    void notaDePagina_comPosicaoNaPagina() {
        var r = marcacoes.aplicar(List.of(new Mudanca(UUID.randomUUID(), UUID.randomUUID(), segmento, 3, Tipo.NOTA_PAGINA, null, null, null, 0.4, 0.38,
            null, "Art. 12: prazo de 15 dias", false, T)));
        assertThat(r.aplicadas()).hasSize(1);
        var m = marcacoes.vigentes(segmento).get(0);
        assertThat(m.posicaoX()).isEqualTo(0.4);
        assertThat(m.texto()).startsWith("Art. 12");
    }

    // ---------- Rotas e D-121 ----------

    @Test
    void d121_leitura_comAUltimaPagina_osBlocosEAsMarcacoes() throws Exception {
        marcacoes.aplicar(List.of(destaque(UUID.randomUUID(), "IMPORTANTE", T)));
        mockMvc.perform(get("/api/segmentos/{id}/leitura", segmento2)).andExpect(status().isOk())
            .andExpect(jsonPath("$.posicao").value(2)).andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.ultimaPagina").doesNotExist()).andExpect(jsonPath("$.marcacoes.length()").value(0));
        mockMvc.perform(put("/api/segmentos/{id}/leitura", segmento).contentType(MediaType.APPLICATION_JSON).content("{\"pagina\":7}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/segmentos/{id}/leitura", segmento).contentType(MediaType.APPLICATION_JSON).content("{\"pagina\":9}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/segmentos/{id}/leitura", segmento)).andExpect(status().isOk())
            .andExpect(jsonPath("$.ultimaPagina").value(9)).andExpect(jsonPath("$.assuntoId").value(assunto))
            .andExpect(jsonPath("$.marcacoes[0].cor").value("IMPORTANTE")).andExpect(jsonPath("$.marcacoes[0].retangulos[0][2]").value(0.5));
        mockMvc.perform(put("/api/segmentos/{id}/leitura", segmento).contentType(MediaType.APPLICATION_JSON).content("{\"pagina\":0}"))
            .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/segmentos/{id}/leitura", 999_999_999L)).andExpect(status().isNotFound());
    }

    @Test
    void loteDeMudancasPelaRota_eHistorico() throws Exception {
        var chave = UUID.randomUUID();
        var mudanca = UUID.randomUUID();
        var corpo = "[{\"mudancaId\":\"" + mudanca + "\",\"marcacaoChave\":\"" + chave + "\",\"segmentoId\":" + segmento
            + ",\"pagina\":7,\"tipo\":\"DESTAQUE\",\"retangulos\":[[0.1,0.2,0.5,0.02]],\"citacao\":\"anáfora\",\"cor\":\"DECORAR\","
            + "\"removida\":false,\"editadaEm\":\"2026-11-01T21:00:00Z\"}]";
        mockMvc.perform(post("/api/marcacoes/mudancas").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(status().isOk()).andExpect(jsonPath("$.aplicadas[0]").value(mudanca.toString()));
        mockMvc.perform(post("/api/marcacoes/mudancas").contentType(MediaType.APPLICATION_JSON).content(corpo))
            .andExpect(jsonPath("$.repetidas[0]").value(mudanca.toString()));
        mockMvc.perform(get("/api/segmentos/{id}/marcacoes/historico", segmento)).andExpect(status().isOk())
            .andExpect(jsonPath("$.versoes.length()").value(1)).andExpect(jsonPath("$.removidas.length()").value(0));
    }
}
