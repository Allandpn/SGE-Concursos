package br.com.estudos.erro;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.sessao.SessaoRequest;
import br.com.estudos.sessao.SessaoService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.enums.TipoSessao;

/**
 * Erro de domínio (docs/SPRINT-7-METRICAS.md §6) — D-46: assunto/sessão
 * inexistentes viram 404 com codigo, mesmo padrão de AssuntoErroDominioTest.
 * Item 7.7 de PROGRESSO.md.
 */
@Transactional
class ErroDominioTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Autowired
    private SessaoService sessaoService;

    private Assunto novoAssunto() {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Erro " + UUID.randomUUID(), TipoPeso.MEDIO));
        return assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Assunto Erro", TipoPeso.MEDIO, (short) 3, 1));
    }

    @Test
    void assuntoInexistente_devolve404() throws Exception {
        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": 999999, "descricao": "Confundi datas", "causa": "DESATENCAO", "confianca": "BAIXA"}
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("ASSUNTO_INEXISTENTE"));
    }

    @Test
    void assuntoIdAusente_devolve422() throws Exception {
        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"descricao": "Sem assunto no corpo", "causa": "DESATENCAO", "confianca": "BAIXA"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("ASSUNTO_OBRIGATORIO"));
    }

    @Test
    void sessaoInexistente_devolve404() throws Exception {
        var assunto = novoAssunto();

        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "sessaoId": 999999, "descricao": "Errei a questão", "causa": "CHUTE", "confianca": "BAIXA"}
                    """.formatted(assunto.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.codigo").value("SESSAO_INEXISTENTE"));
    }

    @Test
    void descricaoAusente_devolve422() throws Exception {
        var assunto = novoAssunto();

        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "causa": "CHUTE", "confianca": "BAIXA"}
                    """.formatted(assunto.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("DESCRICAO_OBRIGATORIA"));
    }

    @Test
    void causaAusente_devolve422() throws Exception {
        var assunto = novoAssunto();

        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "descricao": "Não lembrei do prazo", "confianca": "BAIXA"}
                    """.formatted(assunto.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("CAUSA_OBRIGATORIA"));
    }

    @Test
    void confiancaAusente_devolve422() throws Exception {
        var assunto = novoAssunto();

        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "descricao": "Não lembrei do prazo", "causa": "GESTAO_TEMPO"}
                    """.formatted(assunto.getId())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.codigo").value("CONFIANCA_OBRIGATORIA"));
    }

    @Test
    void registraSemSessao_devolve201() throws Exception {
        var assunto = novoAssunto();

        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "descricao": "Achei que era só decorar", "causa": "FALTA_CONHECIMENTO", "confianca": "ALTA"}
                    """.formatted(assunto.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.assuntoId").value(assunto.getId()))
            .andExpect(jsonPath("$.sessaoId").value(nullValue()))
            .andExpect(jsonPath("$.resolvido").value(false));
    }

    @Test
    void registraComSessao_apareceNaListagemDoAssunto() throws Exception {
        var assunto = novoAssunto();
        var sessao = sessaoService.registrar(new SessaoRequest(
            assunto.getId(), TipoSessao.ESTUDO, LocalDate.now(), 20,
            null, null, null, null, null, null, UUID.randomUUID()));

        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "sessaoId": %d, "descricao": "Troquei o sinal", "causa": "DESATENCAO", "confianca": "MEDIA"}
                    """.formatted(assunto.getId(), sessao.getId())))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/erros").param("assuntoId", assunto.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sessaoId").value(sessao.getId()));
    }
}
