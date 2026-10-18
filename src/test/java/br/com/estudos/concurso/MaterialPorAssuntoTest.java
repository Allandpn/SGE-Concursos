package br.com.estudos.concurso;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.segmento.MaterialService;
import br.com.estudos.segmento.SegmentoRequest;
import br.com.estudos.segmento.SegmentoService;
import br.com.estudos.shared.IntegracaoTestBase;

/**
 * Material por assunto (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §5A): situação, minutos,
 * páginas e motivo, tudo derivado (D-16) dos segmentos e da lacuna ativos.
 */
class MaterialPorAssuntoTest extends IntegracaoTestBase {

    @Autowired private AssuntoRepository assuntoRepository;
    @Autowired private SegmentoService segmentoService;
    @Autowired private MaterialService materialService;

    /** Três assuntos de uma disciplina, em ordem 1-2-3; o concurso vira o alvo. Devolve o id do concurso. */
    private int concursoComTresAssuntos(String s) throws Exception {
        var csv = """
            disciplina,assunto,peso,ordem,chaveExterna
            Material %1$s,Primeiro %1$s,ALTO,1,m1-%1$s
            Material %1$s,Segundo %1$s,MEDIO,2,m2-%1$s
            Material %1$s,Terceiro %1$s,BAIXO,3,m3-%1$s
            """.formatted(s);
        var json = "{\"slug\": \"material-%1$s\", \"nome\": \"Material %1$s\"}".formatted(s);
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar")
                .file(new MockMultipartFile("arquivo", "assuntos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("concurso", "concurso.json", "application/json", json.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk());
        return JsonPath.read(mockMvc.perform(get("/api/concursos/ativo")).andReturn()
            .getResponse().getContentAsString(), "$.id");
    }

    private Long assunto(String chave) {
        return assuntoRepository.findByChaveExterna(chave).orElseThrow().getId();
    }

    @Test
    void listaNaOrdemDoEdital_comSituacaoMinutosEPaginas() throws Exception {
        var s = Long.toString(System.nanoTime());
        var concurso = concursoComTresAssuntos(s);
        var primeiro = assunto("m1-" + s);
        segmentoService.criarOuAtualizar(new SegmentoRequest(primeiro, "seg-" + UUID.randomUUID(), 1, "/material/a.pdf", 3, 12, 40));
        segmentoService.criarOuAtualizar(new SegmentoRequest(primeiro, "seg-" + UUID.randomUUID(), 2, "/material/b.pdf", 13, 14, 15));
        // Sem páginas nem estimativa: conta o segmento, não soma nada.
        segmentoService.criarOuAtualizar(new SegmentoRequest(assunto("m2-" + s), "seg-" + UUID.randomUUID(), 1, "/material/c.pdf", null, null, null));

        mockMvc.perform(get("/api/concursos/{id}/material", concurso))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].assunto").value("Primeiro " + s))
            .andExpect(jsonPath("$[0].situacao").value("PRONTO"))
            .andExpect(jsonPath("$[0].segmentos").value(2))
            .andExpect(jsonPath("$[0].minutos").value(55))
            .andExpect(jsonPath("$[0].paginas").value(12)) // 3-12 = 10 páginas; 13-14 = 2
            .andExpect(jsonPath("$[0].motivo").isEmpty())
            .andExpect(jsonPath("$[1].situacao").value("PRONTO"))
            .andExpect(jsonPath("$[1].segmentos").value(1))
            .andExpect(jsonPath("$[1].minutos").value(0))
            .andExpect(jsonPath("$[1].paginas").value(0))
            .andExpect(jsonPath("$[2].situacao").value("A_PREPARAR"))
            .andExpect(jsonPath("$[2].segmentos").value(0));
    }

    @Test
    void lacunaMostraOMotivo_segmentoVence() throws Exception {
        var s = Long.toString(System.nanoTime());
        var concurso = concursoComTresAssuntos(s);
        materialService.registrarLacuna("m3-" + s, "o acervo não ensina");
        materialService.registrarLacuna("m1-" + s, "lacuna antiga");
        segmentoService.criarOuAtualizar(new SegmentoRequest(assunto("m1-" + s), "seg-" + UUID.randomUUID(), 1, "/material/a.pdf", 1, 5, 20));

        mockMvc.perform(get("/api/concursos/{id}/material", concurso))
            .andExpect(jsonPath("$[2].situacao").value("SEM_MATERIAL"))
            .andExpect(jsonPath("$[2].motivo").value("o acervo não ensina"))
            .andExpect(jsonPath("$[0].situacao").value("PRONTO"))
            .andExpect(jsonPath("$[0].motivo").isEmpty());
    }

    @Test
    void arquivadoELacunaDescartadaNaoEntram() throws Exception {
        var s = Long.toString(System.nanoTime());
        var concurso = concursoComTresAssuntos(s);
        segmentoService.criarOuAtualizar(new SegmentoRequest(assunto("m1-" + s), "seg-" + UUID.randomUUID(), 1, "/material/a.pdf", 1, 10, 40));
        materialService.registrarLacuna("m2-" + s, "o acervo não ensina");

        materialService.refazer(List.of(assunto("m1-" + s), assunto("m2-" + s)));

        var corpo = mockMvc.perform(get("/api/concursos/{id}/material", concurso))
            .andExpect(jsonPath("$[0].situacao").value("A_PREPARAR"))
            .andExpect(jsonPath("$[0].segmentos").value(0))
            .andExpect(jsonPath("$[0].minutos").value(0))
            .andExpect(jsonPath("$[1].situacao").value("A_PREPARAR"))
            .andExpect(jsonPath("$[1].motivo").isEmpty())
            .andReturn().getResponse().getContentAsString();
        assertTrue(corpo.contains("A_PREPARAR"));
    }

    @Test
    void concursoInexistente_404() throws Exception {
        mockMvc.perform(get("/api/concursos/{id}/material", 999999999))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("CONCURSO_INEXISTENTE"));
    }
}
