package br.com.estudos.importacao;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Exportação de segmentos — round-trip via chaveExternaSegmento (D-53),
 * mesmo layout do import (docs/SPRINT-10-SEGMENTO.md §5). Sem coluna `id`
 * no CSV (diferente de ExportacaoAssuntoTest) — o que garante o round-trip
 * aqui é a chave externa, não um identificador interno exposto.
 */
class ExportacaoSegmentoTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Test
    void confirmarExportarEReimportar_naoDuplica() throws Exception {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Exportacao Segmento", TipoPeso.MEDIO));
        var assunto = assuntoService.criar(new AssuntoRequest(
            disciplina.getId(), "Assunto Exportacao Segmento", TipoPeso.MEDIO, (short) 3, 1, "assunto-exportacao"));

        var chaveExternaSegmento = UUID.randomUUID().toString();
        var csvOriginal = """
            chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo
            %s,%s,1,https://drive.example/segmento-exportacao
            """.formatted(chaveExternaSegmento, assunto.getChaveExterna());
        mockMvc.perform(multipart("/api/segmentos/importacoes/confirmar").file(csv(csvOriginal)))
            .andExpect(jsonPath("$.segmentosNovos").value(1));

        var exportado = mockMvc.perform(get("/api/segmentos/exportacao"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertTrue(exportado.contains(chaveExternaSegmento), "exportação precisa conter o segmento confirmado");
        assertTrue(exportado.contains("https://drive.example/segmento-exportacao"));

        // Reimportar o CSV exportado tem que ATUALIZAR o mesmo segmento, nunca
        // duplicar — é exatamente isso que faz o round-trip valer a pena.
        mockMvc.perform(multipart("/api/segmentos/importacoes/confirmar")
                .file(new MockMultipartFile("arquivo", "segmentos.csv", "text/csv", exportado.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.segmentosNovos").value(0))
            .andExpect(jsonPath("$.recusadas").isEmpty());
    }

    private MockMultipartFile csv(String conteudo) {
        return new MockMultipartFile("arquivo", "segmentos.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }
}
