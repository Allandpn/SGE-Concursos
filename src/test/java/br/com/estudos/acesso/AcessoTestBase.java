package br.com.estudos.acesso;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

import br.com.estudos.foco.RelogioMutavel;
import br.com.estudos.shared.IntegracaoTestBase;
import jakarta.servlet.http.Cookie;

/**
 * Base dos testes do login (docs/SPRINT-30-LOGIN.md §6). Não estende IntegracaoTestBase de propósito: aquela base
 * desliga o filtro de sessão (os testes antigos usam a API sem login), e esta precisa dele ligado. O mesmo Postgres real.
 */
@Tag("integracao")
@SpringBootTest
@AutoConfigureMockMvc
@Import(AcessoTestBase.Config.class)
abstract class AcessoTestBase {

    /** O mesmo contêiner da IntegracaoTestBase (um Postgres só na suíte). */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = IntegracaoTestBase.POSTGRES;

    static final String HOST = "eagora.tail0000.ts.net:8443";
    static final String ORIGEM = "https://" + HOST;
    static final Instant T0 = Instant.parse("2026-11-01T12:00:00Z");
    static final RelogioMutavel RELOGIO = new RelogioMutavel(T0, ZoneId.of("America/Sao_Paulo"));

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        RelogioMutavel relogioDoAcesso() {
            return RELOGIO;
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.hikari.maximum-pool-size", () -> "3");
        registro.add("spring.datasource.hikari.minimum-idle", () -> "1");
        registro.add("estudos.foco.varredura-cron", () -> "-");
        registro.add("estudos.acesso.desligado-em-teste", () -> "false");
        registro.add("estudos.acesso.atraso-falha-ms", () -> "0");
        registro.add("estudos.interno.token", () -> "token-de-teste");
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected FreioDeTentativas freio;

    @BeforeEach
    void limparAcesso() {
        jdbc.execute("TRUNCATE sessao_acesso, codigo_recuperacao, passkey RESTART IDENTITY CASCADE");
        freio.limpar();
        RELOGIO.definir(T0);
    }

    /** Como o `tailscale serve` entrega um pedido de um usuário da tailnet. */
    static MockHttpServletRequestBuilder tailnet(MockHttpServletRequestBuilder b) {
        return b.header("X-Forwarded-Host", HOST).header("X-Forwarded-Proto", "https").header("X-Forwarded-For", "100.64.0.2")
            .header(OrigemDoPedido.IDENTIDADE_TAILNET, "titular@example.com").header("Origin", ORIGEM);
    }

    /** Como o `tailscale funnel` entrega um pedido da internet pública. */
    static MockHttpServletRequestBuilder publico(MockHttpServletRequestBuilder b, String ip) {
        return b.header("X-Forwarded-Host", HOST).header("X-Forwarded-Proto", "https").header("X-Forwarded-For", ip)
            .header(OrigemDoPedido.MARCA_FUNNEL, "?1").header("Origin", ORIGEM);
    }

    static MockHttpServletRequestBuilder com(MockHttpServletRequestBuilder b, Cookie sessao) {
        return publico(b, "203.0.113.9").cookie(sessao);
    }

    /** O primeiro cadastro, pela tailnet; devolve o cookie da sessão aberta. */
    Cookie cadastrarPrimeiro(AutenticadorFalso autenticador) throws Exception {
        var opcoes = mvc.perform(tailnet(post("/api/acesso/primeiro/opcoes"))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var credencial = autenticador.cadastrar(opcoes, ORIGEM);
        var resposta = mvc.perform(tailnet(post("/api/acesso/primeiro")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pedidoId\":\"" + AutenticadorFalso.pedidoId(opcoes) + "\",\"credencial\":" + credencial + ",\"nome\":\"Celular\"}"))
            .andExpect(status().isOk()).andReturn().getResponse();
        ultimosCodigos = AutenticadorFalso.arvore(resposta.getContentAsString()).get("codigos");
        return resposta.getCookie(SessaoAcessoFilter.COOKIE);
    }

    protected tools.jackson.databind.JsonNode ultimosCodigos;

    /** Entrar com passkey, de fora; devolve a resposta de `/entrar`. */
    org.springframework.mock.web.MockHttpServletResponse entrar(AutenticadorFalso autenticador, String ip) throws Exception {
        var opcoes = mvc.perform(publico(post("/api/acesso/entrar/opcoes"), ip)).andReturn().getResponse().getContentAsString();
        var credencial = autenticador.assinar(opcoes, ORIGEM);
        return mvc.perform(publico(post("/api/acesso/entrar"), ip).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pedidoId\":\"" + AutenticadorFalso.pedidoId(opcoes) + "\",\"credencial\":" + credencial + "}"))
            .andReturn().getResponse();
    }
}
