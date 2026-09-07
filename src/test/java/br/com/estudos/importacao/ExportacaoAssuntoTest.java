package br.com.estudos.importacao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** Exportação CSV — assunto arquivado não entra (docs/SPRINT-2-CADASTRO.md §6). Item 2.8. */
class ExportacaoAssuntoTest extends IntegracaoTestBase {

    @Autowired
    private DisciplinaService disciplinaService;

    @Autowired
    private AssuntoService assuntoService;

    @Test
    void assuntoArquivado_naoApareceNaExportacao() throws Exception {
        var disciplina = disciplinaService.criar(new DisciplinaRequest("Disciplina Exportacao", TipoPeso.MEDIO));
        assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Assunto Ativo", TipoPeso.ALTO, (short) 3, 1, null));
        var arquivado = assuntoService.criar(new AssuntoRequest(disciplina.getId(), "Assunto Arquivado", TipoPeso.ALTO, (short) 3, 2, null));
        assuntoService.arquivar(arquivado.getId());

        var resposta = mockMvc.perform(get("/api/assuntos/exportacao"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertTrue(resposta.contains("Assunto Ativo"));
        assertFalse(resposta.contains("Assunto Arquivado"));
    }
}
