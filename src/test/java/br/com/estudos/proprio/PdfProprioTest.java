package br.com.estudos.proprio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.sessao.SessaoRequest;
import br.com.estudos.sessao.SessaoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.enums.TipoSessao;
import br.com.estudos.turno.TurnoService;
import tools.jackson.databind.json.JsonMapper;

/** D-129 a D-132 (docs/SPRINT-33-PDF-PROPRIO.md §6). */
class PdfProprioTest extends IntegracaoTestBase {

    static final Path EDITAIS;

    static {
        try {
            EDITAIS = Files.createTempDirectory("editais-proprios");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void editais(DynamicPropertyRegistry r) {
        r.add("estudos.editais.dir", EDITAIS::toString);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;
    @Autowired SessaoService sessoes;
    @Autowired TurnoService turno;
    @Autowired br.com.estudos.segmento.SegmentoRepository segmentos;

    long assunto;

    @BeforeEach
    void assunto() {
        var d = disciplinas.criar(new DisciplinaRequest("Disciplina PDF " + System.nanoTime(), TipoPeso.MEDIO));
        assunto = assuntos.criar(new AssuntoRequest(d.getId(), "Assunto PDF " + System.nanoTime(), TipoPeso.MEDIO, (short) 3, 1, null)).getId();
    }

    static byte[] pdf(int paginas, boolean comTexto) throws IOException {
        try (var doc = new PDDocument(); var saida = new ByteArrayOutputStream()) {
            for (int p = 1; p <= paginas; p++) {
                var pagina = new PDPage();
                doc.addPage(pagina);
                if (comTexto) {
                    try (var c = new PDPageContentStream(doc, pagina)) {
                        c.beginText();
                        c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                        c.newLineAtOffset(50, 700);
                        c.showText("Pagina " + p + ": texto de estudo sobre o assunto, com conteudo suficiente para contar como texto.");
                        c.endText();
                    }
                }
            }
            doc.save(saida);
            return saida.toByteArray();
        }
    }

    long enviar(byte[] conteudo, String tipo) throws Exception {
        var r = mockMvc.perform(multipart("/api/assuntos/{id}/pdfs", assunto)
                .file(new MockMultipartFile("arquivo", "meu resumo.pdf", "application/pdf", conteudo)).param("tipo", tipo))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(r).get("id").asLong();
    }

    @Test
    void d129_cortaEmBlocosDepoisDosExistentes_eServeOsArquivos() throws Exception {
        jdbc.update("INSERT INTO segmento (assunto_id, chave_externa, ordem, arquivo) VALUES (?, ?, 1, '/material/x.pdf')", assunto, "acervo-" + System.nanoTime());
        mockMvc.perform(multipart("/api/assuntos/{id}/pdfs", assunto)
                .file(new MockMultipartFile("arquivo", "meu resumo.pdf", "application/pdf", pdf(45, true))).param("tipo", "APOSTILA"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.blocos").value(2)).andExpect(jsonPath("$.minutos").value(90))
            .andExpect(jsonPath("$.semTexto").value(false)).andExpect(jsonPath("$.nome").value("meu resumo.pdf"));
        var blocos = jdbc.queryForList("SELECT ordem || ':' || pagina_inicial || '-' || pagina_final || ':' || arquivo FROM segmento WHERE assunto_id = ? AND pdf_proprio_id IS NOT NULL ORDER BY ordem",
            String.class, assunto);
        assertThat(blocos).hasSize(2);
        assertThat(blocos.get(0)).startsWith("2:1-30:/material/proprios/");
        assertThat(blocos.get(1)).startsWith("3:31-45:");
        var arquivo = blocos.get(0).substring(blocos.get(0).indexOf("/material/"));
        var resposta = mockMvc.perform(get(arquivo)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(resposta.getContentAsByteArray().length).isGreaterThan(500);
        mockMvc.perform(get("/api/assuntos/{id}/pdfs", assunto)).andExpect(jsonPath("$[0].segmentos.length()").value(2));
    }

    @Test
    void d129_leiSecaCortaMenor_eArquivoInvalidoERecusado() throws Exception {
        mockMvc.perform(multipart("/api/assuntos/{id}/pdfs", assunto)
                .file(new MockMultipartFile("arquivo", "lei.pdf", "application/pdf", pdf(40, true))).param("tipo", "LEI_SECA"))
            .andExpect(jsonPath("$.blocos").value(2)).andExpect(jsonPath("$.minutos").value(120));
        mockMvc.perform(multipart("/api/assuntos/{id}/pdfs", assunto)
                .file(new MockMultipartFile("arquivo", "x.pdf", "application/pdf", "não é pdf".getBytes())).param("tipo", "APOSTILA"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PDF_INVALIDO"));
        mockMvc.perform(multipart("/api/assuntos/{id}/pdfs", assunto)
                .file(new MockMultipartFile("arquivo", "x.pdf", "application/pdf", pdf(3, true))).param("tipo", "OUTRO"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("TIPO_INVALIDO"));
    }

    @Test
    void d130_semTextoEAceito_eMarcado() throws Exception {
        mockMvc.perform(multipart("/api/assuntos/{id}/pdfs", assunto)
                .file(new MockMultipartFile("arquivo", "scan.pdf", "application/pdf", pdf(10, false))).param("tipo", "LEI_SECA"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.semTexto").value(true)).andExpect(jsonPath("$.blocos").value(1));
    }

    @Test
    void d132_tirarArquivaSoOsBlocosDele_eNadaSome() throws Exception {
        var um = enviar(pdf(45, true), "APOSTILA");
        var outro = enviar(pdf(10, true), "APOSTILA");
        mockMvc.perform(post("/api/pdfs/{id}/tirar", um)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmento WHERE pdf_proprio_id = ? AND arquivado_em IS NOT NULL", Integer.class, um)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM segmento WHERE pdf_proprio_id = ? AND arquivado_em IS NULL", Integer.class, outro)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pdf_proprio WHERE id = ?", Integer.class, um)).isEqualTo(1);
        mockMvc.perform(get("/api/assuntos/{id}/pdfs", assunto)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(outro));
        mockMvc.perform(post("/api/pdfs/{id}/tirar", 999_999_999L)).andExpect(status().isNotFound());
    }

    @Test
    void d131_assuntoNaEscada_blocoProprioViraLeituraSugerida_eARevisaoNaoMuda() throws Exception {
        sessoes.registrar(new SessaoRequest(assunto, TipoSessao.ESTUDO, LocalDate.now().minusDays(2), 50, null, null, null, null, null, null,
            UUID.randomUUID(), null)); // estudo do assunto inteiro: a escada começa
        var revisao = jdbc.queryForObject("SELECT id || ':' || data_prevista FROM revisao WHERE assunto_id = ? AND situacao = 'PENDENTE'", String.class, assunto);
        enviar(pdf(10, true), "APOSTILA");
        var plano = turno.plano();
        // Pode haver outro assunto em estudo no banco de testes; se a continuação sugerida for deste assunto, é o bloco próprio.
        var bloco = jdbc.queryForObject("SELECT id FROM segmento WHERE assunto_id = ? AND pdf_proprio_id IS NOT NULL", Long.class, assunto);
        assertThat(segmentos.proximoProprioNaoLidoNaEscada()).contains(bloco);
        if (plano.blocoConteudo() != null && plano.blocoConteudo().id() == assunto) {
            assertThat(plano.blocoContinuacao()).isTrue();
            assertThat(plano.segmentoSugerido().id()).isEqualTo(bloco);
        }
        sessoes.registrar(new SessaoRequest(assunto, TipoSessao.ESTUDO, LocalDate.now(), 20, null, null, null, null, null, null,
            UUID.randomUUID(), bloco));
        assertThat(jdbc.queryForObject("SELECT id || ':' || data_prevista FROM revisao WHERE assunto_id = ? AND situacao = 'PENDENTE'", String.class, assunto))
            .isEqualTo(revisao);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revisao WHERE assunto_id = ?", Integer.class, assunto)).isEqualTo(1);
        assertThat(segmentos.proximoProprioNaoLidoNaEscada().filter(bloco::equals)).isEmpty(); // lido: sai da sugestão
    }
}
