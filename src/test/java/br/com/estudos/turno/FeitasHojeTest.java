package br.com.estudos.turno;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;

/** O progresso do dia em Hoje (04_FRONTEND §8B): o plano conta as sessões registradas hoje, e só as de hoje. */
@Transactional
class FeitasHojeTest extends IntegracaoTestBase {

    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;

    long feitasHoje() throws Exception {
        var corpo = mockMvc.perform(get("/api/turno/plano")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(corpo, "$.feitasHoje")).longValue();
    }

    void estudo(long assunto, LocalDate data) throws Exception {
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content("""
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 30, "tentativaId": "%s"}"""
            .formatted(assunto, data, UUID.randomUUID()))).andExpect(status().isCreated());
    }

    @Test
    void contaSoAsSessoesDeHoje() throws Exception {
        var d = disciplinas.criar(new DisciplinaRequest("Hoje " + System.nanoTime(), TipoPeso.MEDIO)).getId();
        var a = assuntos.criar(new AssuntoRequest(d, "Assunto", TipoPeso.MEDIO, (short) 3, 1, null)).getId();
        var antes = feitasHoje();
        estudo(a, LocalDate.now().minusDays(1));
        org.assertj.core.api.Assertions.assertThat(feitasHoje()).isEqualTo(antes);
        estudo(a, LocalDate.now());
        org.assertj.core.api.Assertions.assertThat(feitasHoje()).isEqualTo(antes + 1);
    }
}
