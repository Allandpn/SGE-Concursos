package br.com.estudos.pergunta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.edital.FalhaIaException;
import br.com.estudos.edital.ServicoIa;
import br.com.estudos.sessao.SessaoRequest;
import br.com.estudos.sessao.SessaoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.turno.TurnoService;

/** D-122 a D-128 (docs/SPRINT-32-PERGUNTAS.md §6): banco, preparação com IA falsa, seleção, conferência e reestudo. */
class PerguntaTest extends IntegracaoTestBase {

    static final Path MATERIAL;

    static {
        try {
            MATERIAL = Files.createTempDirectory("material-perguntas");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void material(DynamicPropertyRegistry r) {
        r.add("estudos.material.dir", MATERIAL::toString);
    }

    /** Duas perguntas válidas e uma fora da forma (tipo inventado), que deve ser descartada. */
    static final String RESPOSTA_IA = """
        Aqui está: {"perguntas":[
          {"tipo":"EVOCACAO","enunciado":"Quais são os mecanismos de coesão referencial?","marcada":true,
           "pontos":[{"texto":"anáfora e catáfora","principal":true,"pagina":1},{"texto":"elipse","principal":true,"pagina":2},
                     {"texto":"pronomes e sinônimos","principal":false,"pagina":2}]},
          {"tipo":"COMPLETAR","enunciado":"A ____ retoma; a ____ antecipa.","marcada":false,
           "pontos":[{"texto":"anáfora / catáfora","principal":true,"pagina":1}]},
          {"tipo":"RESUMO","enunciado":"Resuma tudo","pontos":[{"texto":"x","principal":true,"pagina":1}]}
        ]}""";

    @MockitoBean ServicoIa ia;
    @Autowired PreparoPerguntas preparo;
    @Autowired PerguntaService perguntas;
    @Autowired SessaoService sessoes;
    @Autowired TurnoService turno;
    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;
    @Autowired PlatformTransactionManager transacoes;

    long assunto;
    long segmento;

    @BeforeEach
    void bloco() throws IOException {
        jdbc.execute("TRUNCATE sessao_ponto, sessao_pergunta, pergunta_ponto, pergunta, pergunta_preparo");
        reset(ia);
        var d = disciplinas.criar(new DisciplinaRequest("Disciplina Perguntas " + System.nanoTime(), TipoPeso.MEDIO));
        assunto = assuntos.criar(new AssuntoRequest(d.getId(), "Coesão " + System.nanoTime(), TipoPeso.MEDIO, (short) 3, 1, null)).getId();
        var nome = "blocos/coesao/segmento-" + System.nanoTime() + ".pdf";
        pdf(MATERIAL.resolve(nome), true);
        segmento = jdbc.queryForObject("""
            INSERT INTO segmento (assunto_id, chave_externa, ordem, arquivo, pagina_inicial, pagina_final) VALUES (?, ?, 1, ?, 1, 2) RETURNING id""",
            Long.class, assunto, "seg-perg-" + System.nanoTime(), "/material/" + nome);
    }

    static void pdf(Path destino, boolean comTexto) throws IOException {
        Files.createDirectories(destino.getParent());
        try (var doc = new PDDocument()) {
            for (int p = 1; p <= 2; p++) {
                var pagina = new PDPage();
                doc.addPage(pagina);
                if (comTexto) {
                    try (var c = new PDPageContentStream(doc, pagina)) {
                        c.beginText();
                        c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                        c.newLineAtOffset(50, 700);
                        for (int l = 0; l < 12; l++) {
                            c.showText("Coesao referencial: a anafora retoma e a catafora antecipa um termo do texto. Linha " + l);
                            c.newLineAtOffset(0, -16);
                        }
                        c.endText();
                    }
                }
            }
            doc.save(destino.toFile());
        }
    }

    SessaoRequest estudo() {
        return new SessaoRequest(assunto, TipoSessao.ESTUDO, LocalDate.now().minusDays(3), 50, null, null, null, null, null, null, UUID.randomUUID(), segmento);
    }

    SessaoRequest recuperacao(ResultadoSessao resultado, PerguntaService.Conferencia conferencia) {
        return new SessaoRequest(assunto, TipoSessao.RECUPERACAO, LocalDate.now(), 6, null, null, null, null, ResultadoSessao.PARCIAL, resultado,
            UUID.randomUUID(), null, null, conferencia);
    }

    void prontoComIa() {
        when(ia.completar(anyString(), anyString())).thenReturn(RESPOSTA_IA);
        sessoes.registrar(estudo());
        preparo.preparar(segmento); // direto no bloco: a fila do banco de testes tem blocos de outras classes
    }

    // ---------- D-123 no banco ----------

    @Test
    void d123_tipoEnunciadoPaginaEPerguntaSemPonto() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO pergunta (segmento_id, tipo, enunciado, criada_em) VALUES (?, 'RESUMO', 'x', now())", segmento))
            .hasMessageContaining("ck_pergunta_d123_tipo");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO pergunta (segmento_id, tipo, enunciado, criada_em) VALUES (?, 'EVOCACAO', '  ', now())", segmento))
            .hasMessageContaining("ck_pergunta_d123_enunciado");
        var tx = new TransactionTemplate(transacoes);
        assertThatThrownBy(() -> tx.executeWithoutResult(s ->
            jdbc.update("INSERT INTO pergunta (segmento_id, tipo, enunciado, criada_em) VALUES (?, 'EVOCACAO', 'Pergunta?', now())", segmento)))
            .hasMessageContaining("D-123");
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            long id = jdbc.queryForObject("INSERT INTO pergunta (segmento_id, tipo, enunciado, criada_em) VALUES (?, 'EVOCACAO', 'P?', now()) RETURNING id", Long.class, segmento);
            jdbc.update("INSERT INTO pergunta_ponto (pergunta_id, ordem, texto, principal, pagina) VALUES (?, 1, 'x', true, 0)", id);
        })).hasMessageContaining("ck_pergunta_ponto_d123_pagina");
    }

    // ---------- D-122 ----------

    @Test
    void d122_estudoDoBlocoEnfileira_aVarreduraPrepara_eDescartaOQueEstaForaDaForma() {
        prontoComIa();
        assertThat(jdbc.queryForObject("SELECT situacao FROM pergunta_preparo WHERE segmento_id = ?", String.class, segmento)).isEqualTo("PRONTA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pergunta WHERE segmento_id = ?", Integer.class, segmento)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pergunta_ponto pp JOIN pergunta p ON p.id = pp.pergunta_id WHERE p.segmento_id = ?", Integer.class, segmento)).isEqualTo(4);
        assertThat(perguntas.estado(assunto)).singleElement().satisfies(e -> {
            assertThat(e.situacao()).isEqualTo("PRONTA");
            assertThat(e.prontas()).isEqualTo(2);
        });
    }

    @Test
    void d122_iaForaFicaPendente_eRetomaDepois() {
        when(ia.completar(anyString(), anyString())).thenThrow(new FalhaIaException("assinatura parada", true));
        sessoes.registrar(estudo());
        assertThat(preparo.preparar(segmento)).isFalse(); // a varredura para quando a IA está fora
        assertThat(jdbc.queryForObject("SELECT situacao FROM pergunta_preparo WHERE segmento_id = ?", String.class, segmento)).isEqualTo("PENDENTE");
        assertThat(jdbc.queryForObject("SELECT tentativas FROM pergunta_preparo WHERE segmento_id = ?", Integer.class, segmento)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pergunta", Integer.class)).isZero();
        reset(ia);
        when(ia.completar(anyString(), anyString())).thenReturn(RESPOSTA_IA);
        assertThat(preparo.preparar(segmento)).isTrue();
        assertThat(jdbc.queryForObject("SELECT situacao FROM pergunta_preparo WHERE segmento_id = ?", String.class, segmento)).isEqualTo("PRONTA");
    }

    @Test
    void d122_respostaInvalidaDuasVezes_falha() {
        when(ia.completar(anyString(), anyString())).thenReturn("não sei responder em JSON");
        sessoes.registrar(estudo());
        preparo.preparar(segmento);
        assertThat(jdbc.queryForObject("SELECT situacao FROM pergunta_preparo WHERE segmento_id = ?", String.class, segmento)).isEqualTo("PENDENTE");
        preparo.preparar(segmento);
        assertThat(jdbc.queryForObject("SELECT situacao FROM pergunta_preparo WHERE segmento_id = ?", String.class, segmento)).isEqualTo("FALHOU");
    }

    @Test
    void d122_pdfSemTexto_naoTemPerguntas() throws IOException {
        var nome = "blocos/coesao/escaneado-" + System.nanoTime() + ".pdf";
        pdf(MATERIAL.resolve(nome), false);
        jdbc.update("UPDATE segmento SET arquivo = ? WHERE id = ?", "/material/" + nome, segmento);
        sessoes.registrar(estudo());
        preparo.preparar(segmento);
        assertThat(jdbc.queryForObject("SELECT situacao FROM pergunta_preparo WHERE segmento_id = ?", String.class, segmento)).isEqualTo("SEM_TEXTO");
    }

    @Test
    void d122_blocosJaLidos_entramNaFila() {
        sessoes.registrar(estudo());
        jdbc.update("DELETE FROM pergunta_preparo");
        preparo.completarFila(1000);
        assertThat(jdbc.queryForObject("SELECT situacao FROM pergunta_preparo WHERE segmento_id = ?", String.class, segmento)).isEqualTo("PENDENTE");
        assertThat(preparo.completarFila(1000)).isZero(); // não enfileira duas vezes
    }

    // ---------- D-124 / D-125 / D-127 ----------

    @Test
    void d124_seleciona_semGabarito_eNuncaVistasPrimeiro() throws Exception {
        prontoComIa();
        var sel = perguntas.selecionar(assunto);
        assertThat(sel).hasSize(2); // só há duas (um bloco lido pediria 3)
        assertThat(sel.get(0).enunciado()).contains("mecanismos"); // marcada vem antes entre as nunca vistas
        mockMvc.perform(get("/api/assuntos/{id}/perguntas", assunto)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].enunciado").exists()).andExpect(jsonPath("$[0].pontos").doesNotExist());
        mockMvc.perform(get("/api/perguntas/gabarito").param("ids", String.valueOf(sel.get(0).id())))
            .andExpect(jsonPath("$[0].pontos.length()").value(3)).andExpect(jsonPath("$[0].pontos[0].pagina").value(1));
    }

    @Test
    void d124_semPerguntas_eARecuperacaoLivre() throws Exception {
        mockMvc.perform(get("/api/assuntos/{id}/perguntas", assunto)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void d127_perguntaRuimSaiDoBanco_eDesfazVolta() throws Exception {
        prontoComIa();
        var alvo = perguntas.selecionar(assunto).get(0).id();
        mockMvc.perform(post("/api/perguntas/{id}/ruim", alvo)).andExpect(status().isNoContent());
        assertThat(perguntas.selecionar(assunto)).extracting(PerguntaService.Pergunta::id).doesNotContain(alvo);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pergunta WHERE id = ?", Integer.class, alvo)).isEqualTo(1);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/perguntas/{id}/ruim", alvo))
            .andExpect(status().isNoContent());
        assertThat(perguntas.selecionar(assunto)).extracting(PerguntaService.Pergunta::id).contains(alvo);
    }

    // ---------- D-126 / D-128 ----------

    @Test
    void d126_d128_falhaGuardaOsPontos_eOReestudoAponta() {
        prontoComIa();
        var sel = perguntas.selecionar(assunto);
        var gab = perguntas.gabarito(sel.stream().map(PerguntaService.Pergunta::id).toList());
        var veio = gab.get(0).pontos().get(0).id(); // só o primeiro ponto da primeira pergunta veio
        sessoes.registrar(recuperacao(ResultadoSessao.FALHA,
            new PerguntaService.Conferencia(sel.stream().map(PerguntaService.Pergunta::id).toList(), List.of(veio))));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessao_ponto", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessao_ponto WHERE veio", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revisao WHERE assunto_id = ? AND situacao = 'PENDENTE'", Integer.class, assunto)).isEqualTo(1);

        var reestudo = turno.plano().reestudos().stream().filter(r -> r.assuntoId() == assunto).findFirst().orElseThrow();
        assertThat(reestudo.pontos()).hasSize(3).allSatisfy(p -> {
            assertThat(p.segmentoId()).isEqualTo(segmento);
            assertThat(p.bloco()).isEqualTo(1);
        });
        // Na próxima recuperação, a pergunta com ponto principal faltando vem na frente das já vistas sem falta.
        assertThat(perguntas.selecionar(assunto)).isNotEmpty();
    }

    @Test
    void d128_parcialNaoCriaReestudo() {
        prontoComIa();
        var sel = perguntas.selecionar(assunto);
        sessoes.registrar(recuperacao(ResultadoSessao.PARCIAL,
            new PerguntaService.Conferencia(sel.stream().map(PerguntaService.Pergunta::id).toList(), List.of())));
        assertThat(turno.plano().reestudos()).noneMatch(r -> r.assuntoId() == assunto);
    }

    @Test
    void d126_conferenciaIgnoraPerguntaDeOutroAssuntoERuim() {
        prontoComIa();
        var sel = perguntas.selecionar(assunto);
        perguntas.marcarRuim(sel.get(1).id(), true);
        sessoes.registrar(recuperacao(ResultadoSessao.SUCESSO,
            new PerguntaService.Conferencia(List.of(sel.get(0).id(), sel.get(1).id(), 999_999L), List.of())));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessao_pergunta", Integer.class)).isEqualTo(1);
    }
}
