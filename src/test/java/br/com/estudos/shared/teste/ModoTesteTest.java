package br.com.estudos.shared.teste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.edital.FalhaIaException;
import br.com.estudos.edital.ServicoIa;
import br.com.estudos.shared.IntegracaoTestBase;
import br.com.estudos.shared.enums.TipoPeso;
import jakarta.servlet.http.Cookie;

/**
 * O modo de teste (ADR-061; docs/SPRINT-41-MODO-DE-TESTE.md §4). Sem {@code @Transactional}: a cópia cruza os dois schemas. O que
 * o teste grava no real é apagado no fim, e o schema {@code teste} é removido.
 */
class ModoTesteTest extends IntegracaoTestBase {

    static final Cookie TESTE = new Cookie(ModoTesteFiltro.COOKIE, "1");

    @Autowired JdbcTemplate jdbc;
    @Autowired ModoTesteService servico;
    @Autowired DisciplinaService disciplinas;
    @Autowired AssuntoService assuntos;
    @Autowired ServicoIa ia;

    Long disciplina;
    Long assunto;

    @AfterEach
    void limpar() {
        jdbc.execute("DROP SCHEMA IF EXISTS teste CASCADE");
        servico.esquecerCache();
        if (assunto != null) {
            jdbc.update("DELETE FROM revisao WHERE assunto_id = ?", assunto);
            jdbc.update("DELETE FROM sessao WHERE assunto_id = ?", assunto);
            jdbc.update("DELETE FROM assunto WHERE id = ?", assunto);
        }
        if (disciplina != null) jdbc.update("DELETE FROM disciplina WHERE id = ?", disciplina);
    }

    long conta(String schema, String tabela) {
        return jdbc.queryForObject("SELECT count(*) FROM " + schema + "." + tabela, Long.class);
    }

    String estudo(LocalDate data) {
        return """
            {"assuntoId": %d, "tipo": "ESTUDO", "data": "%s", "tempoMinutos": 30, "tentativaId": "%s"}""".formatted(assunto, data, UUID.randomUUID());
    }

    @Test
    void entrar_copia_isola_moveAData_recomeca_eRecusaArquivoEIa() throws Exception {
        disciplina = disciplinas.criar(new DisciplinaRequest("Modo teste " + System.nanoTime(), TipoPeso.MEDIO)).getId();
        assunto = assuntos.criar(new AssuntoRequest(disciplina, "Crase", TipoPeso.ALTO, (short) 3, 1, null)).getId();
        mockMvc.perform(post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(estudo(LocalDate.now().minusDays(1))))
            .andExpect(status().isCreated());

        // Entrar: cria o schema, migra e copia; o acesso não vai junto.
        mockMvc.perform(post("/api/modo-teste/entrar")).andExpect(status().isOk())
            .andExpect(jsonPath("$.ativo").value(true)).andExpect(jsonPath("$.preparado").value(true));
        assertThat(conta("teste", "assunto")).isEqualTo(conta("public", "assunto"));
        assertThat(conta("teste", "sessao")).isEqualTo(conta("public", "sessao"));
        assertThat(conta("teste", "revisao")).isEqualTo(conta("public", "revisao"));
        assertThat(jdbc.queryForObject("SELECT nome FROM teste.assunto WHERE id = ?", String.class, assunto)).isEqualTo("Crase");
        assertThat(conta("teste", "passkey")).isZero();
        assertThat(conta("teste", "sessao_acesso")).isZero();

        // Com o cookie, a sessão grava só no teste; sem ele, no real. A identidade segue sem colidir.
        var reais = conta("public", "sessao");
        var doTeste = conta("teste", "sessao");
        mockMvc.perform(post("/api/sessoes").cookie(TESTE).contentType(MediaType.APPLICATION_JSON).content(estudo(LocalDate.now())))
            .andExpect(status().isCreated());
        assertThat(conta("public", "sessao")).isEqualTo(reais);
        assertThat(conta("teste", "sessao")).isEqualTo(doTeste + 1);

        // A data do teste: +3 dias muda o "hoje" do teste e não o do real.
        mockMvc.perform(post("/api/modo-teste/avancar").cookie(TESTE).contentType(MediaType.APPLICATION_JSON).content("{\"dias\": 3}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.deslocamentoDias").value(3));
        mockMvc.perform(get("/api/modo-teste").cookie(TESTE))
            .andExpect(jsonPath("$.ativo").value(true)).andExpect(jsonPath("$.hoje").value(LocalDate.now().plusDays(3).toString()));
        mockMvc.perform(get("/api/modo-teste"))
            .andExpect(jsonPath("$.ativo").value(false)).andExpect(jsonPath("$.hoje").value(LocalDate.now().toString()));
        mockMvc.perform(get("/api/turno/plano").cookie(TESTE)).andExpect(status().isOk());
        mockMvc.perform(post("/api/modo-teste/data").cookie(TESTE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\": \"" + LocalDate.now().plusDays(10) + "\"}"))
            .andExpect(jsonPath("$.deslocamentoDias").value(10));

        // No teste: envio de arquivo e IA recusados.
        mockMvc.perform(multipart("/api/editais").file(new MockMultipartFile("arquivo", "e.pdf", "application/pdf", new byte[] {1})).cookie(TESTE))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MODO_TESTE_SEM_ARQUIVO"));
        ModoTeste.ligar(0);
        try {
            assertThatThrownBy(() -> ia.completar("s", "u")).isInstanceOf(FalhaIaException.class).hasMessageContaining("modo de teste");
        } finally {
            ModoTeste.desligar();
        }

        // Recomeçar: a cópia de novo (a sessão do teste some) e a data volta a hoje.
        mockMvc.perform(post("/api/modo-teste/recomecar").cookie(TESTE)).andExpect(status().isOk())
            .andExpect(jsonPath("$.deslocamentoDias").value(0));
        assertThat(conta("teste", "sessao")).isEqualTo(conta("public", "sessao"));

        // Sair: o cookie é apagado; o schema fica.
        mockMvc.perform(post("/api/modo-teste/sair").cookie(TESTE)).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
        assertThat(servico.preparado()).isTrue();
    }
}
