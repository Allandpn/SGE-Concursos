package br.com.estudos.importacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import br.com.estudos.segmento.SegmentoRepository;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Importação de segmentos — tudo-ou-nada e upsert por chaveExterna
 * (docs/SPRINT-10-SEGMENTO.md §4). Item 10.6/10.8 de PROGRESSO.md.
 *
 * Sem @Transactional, mesmo motivo de ImportacaoAssuntoTest: precisa ver o
 * commit/rollback real de cada endpoint.
 */
class ImportacaoSegmentoTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Autowired
    private SegmentoRepository segmentoRepository;

    private String novoAssuntoComChaveExterna(String sufixo) {
        return novoAssunto(sufixo).getChaveExterna();
    }

    private br.com.estudos.assunto.Assunto novoAssunto(String sufixo) {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Importacao Segmento " + sufixo, TipoPeso.MEDIO));
        return assuntoService.criar(new AssuntoRequest(
            disciplina.getId(), "Assunto Importacao Segmento " + sufixo, TipoPeso.MEDIO, (short) 3, 1, "assunto-" + sufixo));
    }

    @Test
    void validar_resolveMasNaoGrava() throws Exception {
        var chaveExternaAssunto = novoAssuntoComChaveExterna("Validar");
        var chaveExternaSegmento = UUID.randomUUID().toString();
        var csv = """
            chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo
            %s,%s,1,https://drive.example/segmento-validar
            """.formatted(chaveExternaSegmento, chaveExternaAssunto);

        mockMvc.perform(multipart("/api/segmentos/importacoes/validar").file(csv(csv)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.segmentosNovos").value(1))
            .andExpect(jsonPath("$.recusadas").isEmpty());

        assertTrue(segmentoRepository.findByChaveExterna(chaveExternaSegmento).isEmpty(),
            "validar não pode gravar nada no banco, mesmo resolvendo tudo sem erro");
    }

    @Test
    void confirmar_comLinhaRecusada_naoGravaNadaDoArquivo() throws Exception {
        var chaveExternaAssunto = novoAssuntoComChaveExterna("ComErro");
        var chaveExternaValida = UUID.randomUUID().toString();
        var csv = """
            chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo
            %s,%s,1,https://drive.example/segmento-valido
            %s,chave-de-assunto-que-nao-existe,2,https://drive.example/segmento-invalido
            """.formatted(chaveExternaValida, chaveExternaAssunto, UUID.randomUUID());

        mockMvc.perform(multipart("/api/segmentos/importacoes/confirmar").file(csv(csv)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recusadas").isNotEmpty());

        assertTrue(segmentoRepository.findByChaveExterna(chaveExternaValida).isEmpty(),
            "arquivo com QUALQUER linha recusada não pode gravar nada — regra 1, tudo ou nada");
    }

    @Test
    void confirmar_semLinhaRecusada_grava() throws Exception {
        var chaveExternaAssunto = novoAssuntoComChaveExterna("Confirma");
        var chaveExternaSegmento = UUID.randomUUID().toString();
        var csv = """
            chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo
            %s,%s,1,https://drive.example/segmento-confirma
            """.formatted(chaveExternaSegmento, chaveExternaAssunto);

        mockMvc.perform(multipart("/api/segmentos/importacoes/confirmar").file(csv(csv)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.segmentosNovos").value(1))
            .andExpect(jsonPath("$.recusadas").isEmpty());

        assertTrue(segmentoRepository.findByChaveExterna(chaveExternaSegmento).isPresent(),
            "confirmar sem linha recusada tem que gravar de verdade");
    }

    @Test
    void reimportar_mesmaChaveExterna_atualizaSemDuplicar() throws Exception {
        var chaveExternaAssunto = novoAssuntoComChaveExterna("Reimporta");
        var chaveExternaSegmento = UUID.randomUUID().toString();

        var csvOriginal = """
            chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo
            %s,%s,2,https://drive.example/segmento-original
            """.formatted(chaveExternaSegmento, chaveExternaAssunto);
        mockMvc.perform(multipart("/api/segmentos/importacoes/confirmar").file(csv(csvOriginal)))
            .andExpect(jsonPath("$.segmentosNovos").value(1));

        // Reimporta com ORDEM diferente (reordenação) e link corrigido — mesma
        // chaveExternaSegmento precisa achar e atualizar o MESMO registro,
        // nunca criar um segundo (D-18, docs/SPRINT-10-SEGMENTO.md §0).
        var csvCorrigido = """
            chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo
            %s,%s,1,https://drive.example/segmento-corrigido
            """.formatted(chaveExternaSegmento, chaveExternaAssunto);
        mockMvc.perform(multipart("/api/segmentos/importacoes/confirmar").file(csv(csvCorrigido)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.segmentosNovos").value(0))
            .andExpect(jsonPath("$.segmentosAtualizados").value(1))
            .andExpect(jsonPath("$.recusadas").isEmpty());

        var segmento = segmentoRepository.findByChaveExterna(chaveExternaSegmento).orElseThrow();
        assertEquals("https://drive.example/segmento-corrigido", segmento.getArquivo());
        assertEquals(1, segmento.getOrdem());
    }

    @Test
    void listar_devolveSegmentosDoAssuntoNaOrdemDeLeitura() throws Exception {
        var assunto = novoAssunto("Listagem");
        var csv = """
            chaveExternaSegmento,chaveExternaAssunto,ordem,arquivo
            %s,%s,2,https://drive.example/segmento-dois
            %s,%s,1,https://drive.example/segmento-um
            """.formatted(
                UUID.randomUUID(), assunto.getChaveExterna(),
                UUID.randomUUID(), assunto.getChaveExterna());
        mockMvc.perform(multipart("/api/segmentos/importacoes/confirmar").file(csv(csv)))
            .andExpect(jsonPath("$.segmentosNovos").value(2));

        mockMvc.perform(get("/api/assuntos/{id}/segmentos", assunto.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$[0].ordem").value(1))
            .andExpect(jsonPath("$[0].arquivo").value("https://drive.example/segmento-um"))
            .andExpect(jsonPath("$[1].ordem").value(2))
            .andExpect(jsonPath("$[1].arquivo").value("https://drive.example/segmento-dois"));
    }

    private MockMultipartFile csv(String conteudo) {
        return new MockMultipartFile("arquivo", "segmentos.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }
}
