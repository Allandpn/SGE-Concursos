package br.com.estudos.conferencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import br.com.estudos.arvore.ArvoreTest;
import br.com.estudos.edital.AnaliseEdital;
import br.com.estudos.edital.AnaliseEditalRepository;
import br.com.estudos.shared.ConferenciaTestBase;
import br.com.estudos.shared.enums.StatusAnaliseEdital;

/** O PDF anexado a uma lacuna (D-97, ADR-049; docs/SPRINT-28): guardar, pendente para o worker, resultado e remoção. */
class AnexoDeLacunaTest extends ConferenciaTestBase {

    @Autowired private AnaliseEditalRepository analises;

    private long id;
    private long assuntoId;
    private String chaveExterna;

    @BeforeEach
    void preparar() throws Exception {
        var analise = new AnaliseEdital("edital.pdf", "texto", "modelo");
        analise.setConcursoNome("MPPR Anexo " + System.nanoTime());
        analise.setCargoEscolhido("Analista TI");
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        id = analises.save(analise).getId();
        mockMvc.perform(internoPost("/internal/arvores", ArvoreTest.V1)).andExpect(status().isOk());
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", EntregaEditalTest.entrega("qconcursos-2026-10-19", 121), id)).andExpect(status().isOk());
        for (long a : jdbc.queryForList("SELECT id FROM edital_assunto", Long.class)) {
            mockMvc.perform(post("/api/editais/analises/{id}/assuntos/{a}/aceitar", id, a)).andExpect(status().isOk());
        }
        for (long t : jdbc.queryForList("SELECT id FROM edital_trecho", Long.class)) {
            mockMvc.perform(post("/api/editais/analises/{id}/trechos/{t}/resolver", id, t).contentType(MediaType.APPLICATION_JSON)
                .content("{\"acao\": \"DESCARTAR\", \"motivo\": \"observação\"}")).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/editais/analises/{id}/conferencia/concluir", id)).andExpect(status().isOk());
        assuntoId = jdbc.queryForObject("SELECT assunto_id FROM edital_assunto WHERE chave = 'a1a1a1a1a1a1a1a1'", Long.class);
        chaveExterna = jdbc.queryForObject("SELECT chave_externa FROM assunto WHERE id = ?", String.class, assuntoId);
        jdbc.update("DELETE FROM segmento WHERE assunto_id = ?", assuntoId);   // o assunto é adotado por nome: pode vir de outro teste com segmentos
        mockMvc.perform(internoPost("/internal/editais/{id}/material", """
            {"assuntos": [{"chave": "a1a1a1a1a1a1a1a1", "situacao": "PARCIAL", "minutos": 90, "fontes": 2,
              "lacunas": [{"parte": "álgebra relacional", "motivo": "nenhum tema do acervo ensina esta parte"}]}]}""", id)).andExpect(status().isOk());
    }

    private static byte[] pdf(int paginas) throws Exception {
        try (var documento = new PDDocument(); var saida = new ByteArrayOutputStream()) {
            for (int i = 0; i < paginas; i++) documento.addPage(new PDPage());
            documento.save(saida);
            return saida.toByteArray();
        }
    }

    private org.springframework.test.web.servlet.ResultActions anexar(String parte, String nome, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/assuntos/{a}/lacunas/anexo", assuntoId)
            .file(new MockMultipartFile("arquivo", nome, "application/pdf", bytes)).param("parte", parte));
    }

    private long anexoId() {
        return jdbc.queryForObject("SELECT id FROM lacuna_anexo", Long.class);
    }

    @Test
    void anexar_guardaOPdfEFicaPendenteParaOWorker() throws Exception {
        anexar("álgebra relacional", "lei.pdf", pdf(3)).andExpect(status().isOk())
            .andExpect(jsonPath("$.lacunas[0].parte").value("álgebra relacional"))
            .andExpect(jsonPath("$.lacunas[0].anexo.estado").value("PENDENTE"))
            .andExpect(jsonPath("$.lacunas[0].anexo.paginas").value(3))
            .andExpect(jsonPath("$.lacunas[0].anexo.nome").value("lei.pdf"));
        var relativo = jdbc.queryForObject("SELECT arquivo FROM lacuna_anexo", String.class);
        assertTrue(relativo.startsWith("anexos/") && relativo.endsWith(".pdf"), relativo);
        assertTrue(Files.exists(EDITAIS.resolve(relativo)), "o PDF fica no volume editais");

        var pendentes = mockMvc.perform(interno(get("/internal/editais/anexos/pendentes"))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertEquals(List.of(chaveExterna), JsonPath.<List<String>>read(pendentes, "$[*].chaveExterna"));
        assertEquals(List.of(relativo), JsonPath.<List<String>>read(pendentes, "$[*].arquivo"));
        assertEquals(List.of(1), JsonPath.<List<Integer>>read(pendentes, "$[*].proximaOrdem"));
    }

    @Test
    void recusa_parteQueNaoELacuna_arquivoQueNaoEPdf_pdfCorrompido_eSegundoAnexo() throws Exception {
        anexar("outra coisa", "lei.pdf", pdf(1)).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("LACUNA_INEXISTENTE"));
        anexar("álgebra relacional", "lei.pdf", "não sou pdf".getBytes(StandardCharsets.UTF_8))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PDF_INVALIDO"));
        anexar("álgebra relacional", "lei.pdf", "%PDF-1.7 lixo".getBytes(StandardCharsets.UTF_8))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("PDF_INVALIDO"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM lacuna_anexo", Integer.class));

        anexar("álgebra relacional", "lei.pdf", pdf(2)).andExpect(status().isOk());
        anexar("álgebra relacional", "outra.pdf", pdf(2)).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ANEXO_JA_EXISTE"));
    }

    @Test
    void pronto_registraOResultado_eNaoSeTiraMais() throws Exception {
        anexar("álgebra relacional", "lei.pdf", pdf(3)).andExpect(status().isOk());
        long anexo = anexoId();
        mockMvc.perform(internoPost("/internal/editais/anexos/{id}/pronto", "{\"segmentos\": 3, \"minutos\": 180}", anexo)).andExpect(status().isOk());
        mockMvc.perform(get("/api/assuntos/{a}/material-da-conferencia", assuntoId))
            .andExpect(jsonPath("$.lacunas[0].anexo.estado").value("PRONTO"))
            .andExpect(jsonPath("$.lacunas[0].anexo.segmentos").value(3))
            .andExpect(jsonPath("$.lacunas[0].anexo.minutos").value(180))
            .andExpect(jsonPath("$.lacunas[0].motivo").value("nenhum tema do acervo ensina esta parte"));   // a lacuna continua declarada
        mockMvc.perform(interno(get("/internal/editais/anexos/pendentes"))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/api/assuntos/{a}/lacunas/anexo", assuntoId).param("parte", "álgebra relacional"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("ANEXO_JA_PRONTO"));
        mockMvc.perform(internoPost("/internal/editais/anexos/{id}/pronto", "{\"segmentos\": 1, \"minutos\": 1}", 999999)).andExpect(status().isNotFound());
    }

    @Test
    void falha_mostraOMotivo_eAnexarDeNovoSubstitui() throws Exception {
        anexar("álgebra relacional", "lei.pdf", pdf(3)).andExpect(status().isOk());
        long anexo = anexoId();
        var primeiro = jdbc.queryForObject("SELECT arquivo FROM lacuna_anexo", String.class);
        mockMvc.perform(internoPost("/internal/editais/anexos/{id}/falha", "{\"erro\": \"o fatiador recusou o PDF\"}", anexo)).andExpect(status().isOk());
        mockMvc.perform(get("/api/assuntos/{a}/material-da-conferencia", assuntoId))
            .andExpect(jsonPath("$.lacunas[0].anexo.estado").value("FALHOU")).andExpect(jsonPath("$.lacunas[0].anexo.erro").value("o fatiador recusou o PDF"));

        anexar("álgebra relacional", "lei2.pdf", pdf(5)).andExpect(status().isOk())
            .andExpect(jsonPath("$.lacunas[0].anexo.estado").value("PENDENTE")).andExpect(jsonPath("$.lacunas[0].anexo.paginas").value(5));
        assertFalse(Files.exists(EDITAIS.resolve(primeiro)), "o PDF da tentativa que falhou sai do volume");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM lacuna_anexo", Integer.class));

        // PENDENTE também se tira
        mockMvc.perform(delete("/api/assuntos/{a}/lacunas/anexo", assuntoId).param("parte", "álgebra relacional")).andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM lacuna_anexo", Integer.class));
        mockMvc.perform(delete("/api/assuntos/{a}/lacunas/anexo", assuntoId).param("parte", "álgebra relacional")).andExpect(status().isNotFound());
    }

    @Test
    void proximaOrdem_continuaANumeracaoDosSegmentosQueOAssuntoJaTem() throws Exception {
        var csv = "chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo,paginaInicial,paginaFinal,tempoEstimadoMin\n"
            + "11111111-1111-1111-1111-111111111111," + chaveExterna + ",1,/material/blocos/x/segmento-01.pdf,1,10,60\n"
            + "22222222-2222-2222-2222-222222222222," + chaveExterna + ",2,/material/blocos/x/segmento-02.pdf,11,20,60\n";
        mockMvc.perform(multipart("/api/segmentos/importacoes/confirmar").file(new MockMultipartFile("arquivo", "segmentos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk());
        anexar("álgebra relacional", "lei.pdf", pdf(2)).andExpect(status().isOk());
        var pendentes = mockMvc.perform(interno(get("/internal/editais/anexos/pendentes"))).andReturn().getResponse().getContentAsString();
        assertEquals(List.of(3), JsonPath.<List<Integer>>read(pendentes, "$[*].proximaOrdem"));
    }
}
