package br.com.estudos.erro;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

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
 * GET /api/erros fora de transação de teste (sem @Transactional na classe,
 * mesmo padrão de ImportacaoAssuntoTest/ExportacaoAssuntoTest) — ADR-034
 * (open-in-view: false) exige join fetch em ErroRepository.findByAssuntoId;
 * sem isto a serialização do Controller lançaria LazyInitializationException
 * aqui. ErroDominioTest é @Transactional e não pegaria essa regressão, pela
 * mesma razão documentada em ADR-034: a sessão do teste mascara o problema.
 */
class ErroListagemTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Autowired
    private SessaoService sessaoService;

    @Test
    void listaComEsemSessao_semLazyInitializationException() throws Exception {
        var disciplina = disciplinaService.criar(
            new DisciplinaRequest("Disciplina Erro Listagem " + UUID.randomUUID(), TipoPeso.MEDIO));
        var assunto = assuntoService.criar(
            new AssuntoRequest(disciplina.getId(), "Assunto Erro Listagem", TipoPeso.MEDIO, (short) 3, 1));
        var sessao = sessaoService.registrar(new SessaoRequest(
            assunto.getId(), TipoSessao.ESTUDO, LocalDate.now(), 20,
            null, null, null, null, null, null, UUID.randomUUID()));

        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "descricao": "Sem sessão", "causa": "DESATENCAO", "confianca": "BAIXA"}
                    """.formatted(assunto.getId())))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/erros")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"assuntoId": %d, "sessaoId": %d, "descricao": "Com sessão", "causa": "CHUTE", "confianca": "MEDIA"}
                    """.formatted(assunto.getId(), sessao.getId())))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/erros").param("assuntoId", assunto.getId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].assuntoId").value(assunto.getId()))
            .andExpect(jsonPath("$[1].assuntoId").value(assunto.getId()));
    }
}