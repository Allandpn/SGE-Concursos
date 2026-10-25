package br.com.estudos.edital;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import br.com.estudos.concurso.ConcursoRepository;
import br.com.estudos.edital.ConclusaoEditalService.PlanoConclusao;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.StatusAnaliseEdital;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Concluir a conferência (01_DOMINIO §3.12, D-62, D-64, D-92; docs/SPRINT-25-CONFERENCIA-EDITAL.md §7):
 * identificador do concurso e chave de cada assunto nascem aqui, e o material entregue pelo
 * planejamento com esse identificador reencontra o mesmo concurso.
 */
class ConclusaoEditalTest extends IntegracaoTestBase {

    @Autowired private ConclusaoEditalService conclusao;
    @Autowired private AnaliseEditalRepository analiseRepository;
    @Autowired private ConcursoRepository concursoRepository;

    private String sufixo;

    @BeforeEach
    void preparar() {
        sufixo = " " + System.nanoTime();
    }

    @Test
    void d64_slugEChaves_eConcursoJsonDoMesmoSlugNaoDuplica() throws Exception {
        var nome = "Concurso D64" + sufixo;
        long concursoId = concluirSimples(nome);

        var entrega = mockMvc.perform(get("/api/concursos/{id}/planejamento", concursoId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assuntos.length()").value(2))
            .andExpect(jsonPath("$.assuntos[0].segmentos").value(0))
            .andExpect(jsonPath("$.assuntos[0].lacuna").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(entrega, "$.slug");
        assertEquals(("concurso-d64" + sufixo).replace(' ', '-'), slug);
        List<String> chaves = JsonPath.read(entrega, "$.assuntos[*].chaveExterna");
        assertTrue(chaves.stream().allMatch(c -> c != null && c.length() == 36), "todo assunto concluído tem chave");
        List<String> nomes = JsonPath.read(entrega, "$.assuntos[*].assunto");

        long antes = concursoRepository.count();
        var csv = "disciplina,assunto,peso,ordem,chaveExterna\n"
            + "Direito D64" + sufixo + "," + nomes.get(0) + ",MEDIO,1," + chaves.get(0) + "\n"
            + "Direito D64" + sufixo + "," + nomes.get(1) + ",MEDIO,2," + chaves.get(1) + "\n";
        var concursoJson = "{\"slug\": \"" + slug + "\", \"nome\": \"" + nome + "\", \"dataProva\": \"2026-12-20\"}";
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar")
                .file(new MockMultipartFile("arquivo", "assuntos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("concurso", "concurso.json", "application/json", concursoJson.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk());

        assertEquals(antes, concursoRepository.count(), "o mesmo slug não cria segundo concurso");
        mockMvc.perform(get("/api/concursos/ativo"))
            .andExpect(jsonPath("$.id").value(concursoId))
            .andExpect(jsonPath("$.cargo").value("Analista"));
    }

    @Test
    void d64_nomeRepetidoGanhaSufixoNoSlug() {
        var nome = "Concurso Repetido" + sufixo;
        long primeiro = concluirSimples(nome);
        long segundo = concluirSimples(nome);

        var slugPrimeiro = concursoRepository.findById(primeiro).orElseThrow().getSlug();
        var slugSegundo = concursoRepository.findById(segundo).orElseThrow().getSlug();
        assertEquals(slugPrimeiro + "-2", slugSegundo);
    }

    @Test
    void concluir_marcaAAnaliseConfirmadaELigaOConcurso() {
        var analise = analiseRepository.save(new AnaliseEdital("edital.pdf", "texto", "modelo"));
        var resultado = conclusao.concluir(analise, plano("Concurso Ligado" + sufixo));

        var salva = analiseRepository.findById(analise.getId()).orElseThrow();
        assertEquals(StatusAnaliseEdital.CONFIRMADA, salva.getStatus());
        assertEquals(resultado.concurso().getId(), salva.getConcursoId());
        assertEquals(2, resultado.assuntos().size(), "um assunto cadastrado por assunto do plano, na ordem");
    }

    // ------------------------------------------------------------ apoio

    private long concluirSimples(String nomeConcurso) {
        var analise = analiseRepository.save(new AnaliseEdital("edital.pdf", "texto", "modelo"));
        return conclusao.concluir(analise, plano(nomeConcurso)).concurso().getId();
    }

    /** Uma disciplina nova e dois assuntos novos. */
    private PlanoConclusao plano(String nomeConcurso) {
        return new PlanoConclusao(nomeConcurso, "Analista", "FCC", java.time.LocalDate.parse("2026-12-20"), List.of(
            new PlanoConclusao.Disciplina("Direito D64" + sufixo, null, TipoPeso.MEDIO, List.of(
                new PlanoConclusao.Assunto("Atos administrativos" + sufixo, null),
                new PlanoConclusao.Assunto("Licitacoes" + sufixo, null)))));
    }
}
