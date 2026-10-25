package br.com.estudos.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base dos testes da conferência do edital (Sprint 25): token interno e volume {@code editais} próprios, para as
 * classes dividirem um só contexto do Spring, e a limpeza das tabelas da conferência. TRUNCATE porque
 * {@code edital_evento} recusa DELETE (D-86) e o gatilho de linha não dispara em TRUNCATE.
 */
public abstract class ConferenciaTestBase extends IntegracaoTestBase {

    public static final String TOKEN = "token-interno-da-conferencia";
    protected static final Path EDITAIS;

    static {
        try {
            EDITAIS = Files.createTempDirectory("editais-conferencia");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void conferencia(DynamicPropertyRegistry registro) {
        registro.add("estudos.interno.token", () -> TOKEN);
        registro.add("estudos.editais.dir", EDITAIS::toString);
    }

    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void limparConferencia() {
        jdbc.execute("TRUNCATE edital_evento, edital_decisao, edital_parte_no, edital_parte, edital_trecho, edital_assunto, "
            + "edital_disciplina, edital_execucao, no_arvore_diferenca, no_arvore, arvore_versao RESTART IDENTITY");
    }

    protected MockHttpServletRequestBuilder interno(MockHttpServletRequestBuilder pedido) {
        return pedido.header("X-Internal-Token", TOKEN);
    }

    protected MockHttpServletRequestBuilder internoPost(String url, String json, Object... variaveis) {
        return interno(post(url, variaveis).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
