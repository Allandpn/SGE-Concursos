package br.com.estudos.concurso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.sessao.SessaoRepository;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Concurso-alvo e histórico (01_DOMINIO §3.8, ADR-042, docs/SPRINT-18-CONCURSOS.md §7),
 * pelo caminho do planejamento: assuntos.csv + concurso.json.
 */
class ConcursoTest extends IntegracaoTestBase {

    @Autowired private AssuntoRepository assuntoRepository;
    @Autowired private SessaoRepository sessaoRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private br.com.estudos.disciplina.DisciplinaRepository disciplinaRepository;

    @Test
    void d58_segundoAtivoRecusadoPelaEscrita() {
        jdbc.update("update concurso set ativo = false where ativo");
        jdbc.update("insert into concurso (nome, origem, ativo) values ('D58 um', 'ANALISE_IA', true)");
        var erro = assertThrows(DataIntegrityViolationException.class,
            () -> jdbc.update("insert into concurso (nome, origem, ativo) values ('D58 dois', 'ANALISE_IA', true)"));
        assertTrue(erro.getMessage().contains("ux_concurso_d58_um_ativo"));
        jdbc.update("update concurso set ativo = false where nome = 'D58 um'");
    }

    @Test
    void trocaDeEdital_aplicaCoberturaMantemEstudoEGuardaHistorico() throws Exception {
        var s = Long.toString(System.nanoTime());
        var disciplina = "Concurso Teste " + s;
        var prova = LocalDate.now().plusDays(90);

        importar("""
            disciplina,assunto,peso,ordem,chaveExterna
            %1$s,Um %2$s,ALTO,1,k1-%2$s
            %1$s,Dois %2$s,MEDIO,2,k2-%2$s
            %1$s,Tres %2$s,BAIXO,3,k3-%2$s
            """.formatted(disciplina, s), "edital-um-" + s, "Edital Um " + s, prova.toString());

        mockMvc.perform(get("/api/concursos/ativo"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nome").value("Edital Um " + s))
            .andExpect(jsonPath("$.origem").value("PLANEJAMENTO"))
            .andExpect(jsonPath("$.totalAssuntos").value(3))
            .andExpect(jsonPath("$.diasParaProva").value(ChronoUnit.DAYS.between(LocalDate.now(), prova)));
        var um = assuntoRepository.findByChaveExterna("k1-" + s).orElseThrow();

        // Estudo feito antes da troca: uma sessão e a revisão que ela agenda.
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
                {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 30, "tentativaId": "%s"}
                """.formatted(um.getId(), LocalDate.now(), UUID.randomUUID())))
            .andExpect(status().isCreated());
        int sessoesAntes = sessaoRepository.findByAssuntoId(um.getId()).size();
        long revisoesAntes = revisoesDe(um.getId());

        // Edital novo: Tres e Um em ordem trocada, Quatro novo, Dois fora. As
        // ordens pedidas colidem com as antigas — antes da Sprint 18 o arquivo
        // inteiro era recusado por D-48.
        importar("""
            disciplina,assunto,peso,ordem,chaveExterna
            %1$s,Tres %2$s,ALTO,1,k3-%2$s
            %1$s,Um %2$s,BAIXO,2,k1-%2$s
            %1$s,Quatro %2$s,MEDIO,3,k4-%2$s
            """.formatted(disciplina, s), "edital-dois-" + s, "Edital Dois " + s, null);

        var tres = assuntoRepository.findByChaveExterna("k3-" + s).orElseThrow();
        var umDepois = assuntoRepository.findById(um.getId()).orElseThrow();
        var dois = assuntoRepository.findByChaveExterna("k2-" + s).orElseThrow();
        var quatro = assuntoRepository.findByChaveExterna("k4-" + s).orElseThrow();
        // D-60: peso e ordem do edital novo; fora do edital vai depois, sem mudar o peso.
        assertEquals(1, tres.getOrdem());
        assertEquals(TipoPeso.ALTO, tres.getPeso());
        assertEquals(2, umDepois.getOrdem());
        assertEquals(TipoPeso.BAIXO, umDepois.getPeso());
        assertEquals(3, quatro.getOrdem());
        assertEquals(4, dois.getOrdem());
        assertEquals(TipoPeso.MEDIO, dois.getPeso());
        assertTrue(dois.isAtivo(), "fora do edital continua como está até o usuário arquivar");
        // D-60: o estudo feito não é tocado.
        assertEquals(sessoesAntes, sessaoRepository.findByAssuntoId(um.getId()).size());
        assertEquals(revisoesAntes, revisoesDe(um.getId()));

        // D-59: o anterior fica no histórico, com o período fechado.
        var historico = mockMvc.perform(get("/api/concursos")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertEquals("Edital Dois " + s, JsonPath.read(historico, "$[0].nome"));
        assertEquals(true, JsonPath.read(historico, "$[0].ativo"));
        java.util.List<Object> anterior = JsonPath.read(historico, "$[?(@.nome == 'Edital Um " + s + "')]");
        assertEquals(1, anterior.size());
        assertNotNull(JsonPath.read(historico, "$[?(@.nome == 'Edital Um " + s + "')].desativadoEm"));
        assertEquals(java.util.List.of(2), JsonPath.read(historico, "$[?(@.nome == 'Edital Um " + s + "')].emComumComAtivo"));
        Integer idUm = ((java.util.List<Integer>) JsonPath.read(historico, "$[?(@.nome == 'Edital Um " + s + "')].id")).get(0);

        // Reativar o Edital Um aplica de novo a cobertura dele.
        mockMvc.perform(post("/api/concursos/{id}/ativar", idUm))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ativo").value(true));
        assertEquals(1, assuntoRepository.findById(um.getId()).orElseThrow().getOrdem());
        assertEquals(TipoPeso.ALTO, assuntoRepository.findById(um.getId()).orElseThrow().getPeso());
        assertEquals(2, assuntoRepository.findByChaveExterna("k2-" + s).orElseThrow().getOrdem());
        assertEquals(3, assuntoRepository.findByChaveExterna("k3-" + s).orElseThrow().getOrdem());
        assertEquals(4, assuntoRepository.findByChaveExterna("k4-" + s).orElseThrow().getOrdem(), "o que o Edital Um não cobre vai depois");
    }

    @Test
    void entregaAoPlanejamento_devolveACoberturaNaOrdemDoEditalSemTocarNada() throws Exception {
        var s = Long.toString(System.nanoTime());
        var disciplina = "Entrega Teste " + s;
        importar("""
            disciplina,assunto,peso,ordem,chaveExterna
            %1$s,Segundo %2$s,BAIXO,2,e2-%2$s
            %1$s,Primeiro %2$s,ALTO,1,e1-%2$s
            """.formatted(disciplina, s), "entrega-" + s, "Entrega " + s, "2027-03-01");
        Integer id = JsonPath.read(mockMvc.perform(get("/api/concursos/ativo")).andReturn()
            .getResponse().getContentAsString(), "$.id");
        var antes = assuntoRepository.findByChaveExterna("e1-" + s).orElseThrow().getOrdem();

        mockMvc.perform(get("/api/concursos/{id}/planejamento", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("entrega-" + s))
            .andExpect(jsonPath("$.nome").value("Entrega " + s))
            .andExpect(jsonPath("$.dataProva").value("2027-03-01"))
            .andExpect(jsonPath("$.assuntos.length()").value(2))
            .andExpect(jsonPath("$.assuntos[0].assunto").value("Primeiro " + s))
            .andExpect(jsonPath("$.assuntos[0].peso").value("ALTO"))
            .andExpect(jsonPath("$.assuntos[0].chaveExterna").value("e1-" + s))
            .andExpect(jsonPath("$.assuntos[1].ordem").value(2));
        assertEquals(antes, assuntoRepository.findByChaveExterna("e1-" + s).orElseThrow().getOrdem(), "só leitura");
        mockMvc.perform(get("/api/concursos/{id}/planejamento", 999999999))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("CONCURSO_INEXISTENTE"));
    }

    @Test
    void concursoJsonInvalido_desfazOArquivoInteiro() throws Exception {
        var s = Long.toString(System.nanoTime());
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar")
                .file(csv("disciplina,assunto,peso,ordem\nJson Invalido " + s + ",A,ALTO,1\n"))
                .file(new MockMultipartFile("concurso", "concurso.json", "application/json", "{\"nome\": \"sem slug\"}".getBytes())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("CONCURSO_JSON_INVALIDO"));
        assertTrue(disciplinaRepository.findByNomeIgnoreCase("Json Invalido " + s).isEmpty(), "tudo ou nada: nada do arquivo fica");
    }

    private long revisoesDe(Long assuntoId) {
        return jdbc.queryForObject("select count(*) from revisao where assunto_id = ?", Long.class, assuntoId);
    }

    private void importar(String csv, String slug, String nome, String dataProva) throws Exception {
        var json = dataProva == null
            ? "{\"slug\": \"%s\", \"nome\": \"%s\"}".formatted(slug, nome)
            : "{\"slug\": \"%s\", \"nome\": \"%s\", \"dataProva\": \"%s\"}".formatted(slug, nome, dataProva);
        mockMvc.perform(multipart("/api/assuntos/importacoes/confirmar")
                .file(csv(csv))
                .file(new MockMultipartFile("concurso", "concurso.json", "application/json", json.getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recusadas").isEmpty());
    }

    private static MockMultipartFile csv(String conteudo) {
        return new MockMultipartFile("arquivo", "assuntos.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }
}
