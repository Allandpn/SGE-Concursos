package br.com.estudos.conferencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.estudos.arvore.ArvoreTest;
import br.com.estudos.edital.AnaliseEdital;
import br.com.estudos.edital.AnaliseEditalRepository;
import br.com.estudos.shared.ConferenciaTestBase;
import br.com.estudos.shared.enums.StatusAnaliseEdital;

/**
 * A entrega do planejamento (contrato edital-revisao/1; 01_DOMINIO §3.12, D-88, D-89;
 * docs/SPRINT-25-CONFERENCIA-EDITAL.md §4), com o formato que o planejamento realmente gera.
 */
public class EntregaEditalTest extends ConferenciaTestBase {

    @Autowired private AnaliseEditalRepository analises;

    private long analiseId;

    @BeforeEach
    void preparar() throws Exception {
        var analise = new AnaliseEdital("edital.pdf", "texto do edital", "modelo");
        analise.setConcursoNome("MPPR 2024");
        analise.setCargoEscolhido("Analista TI");
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        analise.setPdfArquivo("1/edital.pdf");
        analiseId = analises.save(analise).getId();
        mockMvc.perform(internoPost("/internal/arvores", ArvoreTest.V1)).andExpect(status().isOk());
    }

    /** Duas disciplinas, três assuntos: uma parte exata, uma "mais ampla", uma sem nó; um assunto recusado e um trecho sem assunto. */
    public static String entrega(String execucaoArvore, long noDaParte) {
        return """
            {"contrato": "edital-revisao/1",
             "concurso": {"slug": "mppr-2024", "cargo": "Analista TI"},
             "pdf": "mppr",
             "execucao": {"data": "2026-10-24", "arvore": "%s", "modelo": "(proxy do SGE)"},
             "disciplinas": [
               {"nome": "Banco de Dados", "restricao": null, "comum": false, "paginas": [3], "linhas": [10, 20],
                "qconcursos": [-10], "tentativas": 1,
                "texto": "Banco de Dados SQL: window function, álgebra relacional Arquitetura de BD",
                "assuntos": [
                  {"chave": "a1a1a1a1a1a1a1a1", "ordem": 1, "texto": "SQL: window function, álgebra relacional", "erros": [], "alertas": [],
                   "partes": [
                     {"chave": "p1p1p1p1p1p1p1p1", "ordem": 1, "texto": "window function", "contexto": null, "herdado": "SQL",
                      "ligacao": {"nos": [%d], "natureza": "exato", "origem": "ia"}},
                     {"chave": "p2p2p2p2p2p2p2p2", "ordem": 2, "texto": "álgebra relacional", "contexto": null, "herdado": null,
                      "ligacao": {"nos": [12], "natureza": "mais_amplo", "origem": "refino", "antes": [122]}}]},
                  {"chave": "a2a2a2a2a2a2a2a2", "ordem": 2, "texto": "Arquitetura de BD", "erros": ["parte sem cobertura: BD"], "alertas": [],
                   "partes": [
                     {"chave": "p3p3p3p3p3p3p3p3", "ordem": 1, "texto": "Arquitetura de BD", "contexto": null, "herdado": null,
                      "ligacao": null}]}],
                "trechos_sem_assunto": ["Observação solta do edital"], "referencias": []},
               {"nome": "Antropologia", "restricao": "Cargo B", "comum": true, "paginas": [4], "linhas": [21, 30],
                "qconcursos": [-273], "tentativas": 2, "texto": "Antropologia Cultura",
                "assuntos": [
                  {"chave": "a3a3a3a3a3a3a3a3", "ordem": 1, "texto": "Cultura", "erros": [], "alertas": ["contexto de outro assunto"],
                   "partes": [
                     {"chave": "p4p4p4p4p4p4p4p4", "ordem": 1, "texto": "Cultura", "contexto": null, "herdado": null,
                      "ligacao": {"nos": [5], "natureza": "exato", "origem": "decisao", "decisao": "revisão 2026-10-22"}}]}],
                "trechos_sem_assunto": [], "referencias": []}],
             "linhas_fora": [{"linha_inicio": 1, "linha_fim": 9, "paginas": [1, 2], "linhas": ["ANEXO II", "Observações"]}],
             "resumo": {}}""".formatted(execucaoArvore, noDaParte);
    }

    @Test
    void pendentesEDados_mostramAAnaliseQueEsperaOPlanejamento() throws Exception {
        mockMvc.perform(interno(get("/internal/editais/pendentes")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == %d)].cargo".formatted(analiseId)).value("Analista TI"))
            .andExpect(jsonPath("$[?(@.id == %d)].slug".formatted(analiseId)).value("mppr-2024"));
        mockMvc.perform(interno(get("/internal/editais/{id}/dados", analiseId)))
            .andExpect(jsonPath("$.pdf").value("1/edital.pdf"))
            .andExpect(jsonPath("$.arvoreVigente").value("qconcursos-2026-10-19"))
            .andExpect(jsonPath("$.decisoes.length()").value(0));
    }

    @Test
    void entregar_guardaTudoComoVeio_eAAnaliseVaiParaEmConferencia() throws Exception {
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", entrega("qconcursos-2026-10-19", 121), analiseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repetida").value(false))
            .andExpect(jsonPath("$.disciplinas").value(2))
            .andExpect(jsonPath("$.assuntos").value(3))
            .andExpect(jsonPath("$.partes").value(4))
            .andExpect(jsonPath("$.partesSemNo").value(1))
            .andExpect(jsonPath("$.trechosAbertos").value(2)); // um sem assunto + um bloco de linhas fora

        assertEquals(StatusAnaliseEdital.EM_CONFERENCIA, analises.findById(analiseId).orElseThrow().getStatus());
        // D-88: o texto do assunto, como veio.
        assertEquals(List.of("SQL: window function, álgebra relacional", "Arquitetura de BD", "Cultura"),
            jdbc.queryForList("SELECT texto FROM edital_assunto ORDER BY id", String.class));
        assertEquals("DECIDIDA", jdbc.queryForObject("SELECT estado FROM edital_parte WHERE chave = 'p4p4p4p4p4p4p4p4'", String.class),
            "ligação vinda de decisão anterior já nasce decidida");
        assertEquals("MAIS_AMPLO", jdbc.queryForObject("SELECT natureza FROM edital_parte WHERE chave = 'p2p2p2p2p2p2p2p2'", String.class));
        assertEquals("[\"parte sem cobertura: BD\"]", jdbc.queryForObject("SELECT erros FROM edital_assunto WHERE chave = 'a2a2a2a2a2a2a2a2'", String.class));
        assertEquals("[-10]", jdbc.queryForObject("SELECT qconcursos_ids FROM edital_disciplina WHERE nome = 'Banco de Dados'", String.class));
    }

    @Test
    void d89_aMesmaEntregaDuasVezesEhUmaSo() throws Exception {
        var corpo = entrega("qconcursos-2026-10-19", 121);
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", corpo, analiseId)).andExpect(status().isOk());
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", corpo, analiseId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.repetida").value(true))
            .andExpect(jsonPath("$.partes").value(4));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM edital_execucao", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM edital_parte", Integer.class));
    }

    @Test
    void entregaDiferenteDepoisDeEmConferencia_semPedirReprocessar_409() throws Exception {
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", entrega("qconcursos-2026-10-19", 121), analiseId)).andExpect(status().isOk());
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", entrega("qconcursos-2026-10-19", 122), analiseId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ETAPA_INVALIDA"));
    }

    @Test
    void contratoDesconhecido_422() throws Exception {
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao",
                entrega("qconcursos-2026-10-19", 121).replace("edital-revisao/1", "edital-revisao/2"), analiseId))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("CONTRATO_DESCONHECIDO"));
    }

    @Test
    void noForaDaArvore_422_eArvoreNaoCarregada_409_enadaEhGravado() throws Exception {
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", entrega("qconcursos-2026-10-19", 9999), analiseId))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("NO_INEXISTENTE"));
        mockMvc.perform(internoPost("/internal/editais/{id}/revisao", entrega("qconcursos-2030-01-01", 121), analiseId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ARVORE_NAO_CARREGADA"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM edital_execucao", Integer.class));
        assertEquals(StatusAnaliseEdital.SEPARANDO_ASSUNTOS, analises.findById(analiseId).orElseThrow().getStatus());
    }

    @Test
    void falhaDoPlanejamento_marcaAEtapaEAMensagem_eTentarDeNovoDevolve() throws Exception {
        mockMvc.perform(internoPost("/internal/editais/{id}/falha",
                "{\"etapa\": \"QUEBRA\", \"disciplina\": \"Banco de Dados\", \"mensagem\": \"A IA respondeu fora do formato.\"}", analiseId))
            .andExpect(status().isOk());
        var analise = analises.findById(analiseId).orElseThrow();
        assertEquals(StatusAnaliseEdital.FALHOU, analise.getStatus());
        assertEquals("QUEBRA", analise.getEtapaFalha());
        assertEquals("[Banco de Dados] A IA respondeu fora do formato.", analise.getErro());

        mockMvc.perform(post("/api/editais/analises/{id}/tentar-de-novo", analiseId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("SEPARANDO_ASSUNTOS"));

        mockMvc.perform(internoPost("/internal/editais/{id}/falha", "{\"etapa\": \"INVENTADA\", \"mensagem\": \"x\"}", analiseId))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("ETAPA_DESCONHECIDA"));
    }

    @Test
    void rotasInternasExigemToken() throws Exception {
        mockMvc.perform(get("/internal/editais/pendentes")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/internal/editais/{id}/revisao", analiseId)).andExpect(status().isUnauthorized());
    }
}
