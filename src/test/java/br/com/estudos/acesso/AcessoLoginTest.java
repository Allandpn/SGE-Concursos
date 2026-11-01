package br.com.estudos.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** D-107, D-108, D-109, D-110, D-111 pelo caminho HTTP, com um passkey de mentira (docs/SPRINT-30-LOGIN.md §6). */
class AcessoLoginTest extends AcessoTestBase {

    // --- D-108 ------------------------------------------------------------------------------------------------------

    @Test
    void d108_primeiroAcesso_soPelaTailnet_eSoSemPasskey() throws Exception {
        mvc.perform(tailnet(get("/api/acesso/estado"))).andExpect(jsonPath("$.primeiroAcesso").value(true)).andExpect(jsonPath("$.logado").value(false));
        mvc.perform(publico(get("/api/acesso/estado"), "203.0.113.1")).andExpect(jsonPath("$.primeiroAcesso").value(false));
        mvc.perform(publico(post("/api/acesso/primeiro/opcoes"), "203.0.113.1")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.codigo").value("PRIMEIRO_ACESSO_FECHADO"));
        // Sem a identidade da tailnet (um caminho que não passou pelo tailscale serve) também não abre.
        mvc.perform(post("/api/acesso/primeiro/opcoes").header("X-Forwarded-Host", HOST).header("X-Forwarded-Proto", "https"))
            .andExpect(status().isForbidden());

        cadastrarPrimeiro(new AutenticadorFalso(1L));

        mvc.perform(tailnet(get("/api/acesso/estado"))).andExpect(jsonPath("$.primeiroAcesso").value(false));
        mvc.perform(tailnet(post("/api/acesso/primeiro/opcoes"))).andExpect(status().isForbidden());
    }

    @Test
    void d108_marcaDoFunnel_venceAIdentidadeDaTailnet() throws Exception {
        mvc.perform(publico(tailnet(post("/api/acesso/primeiro/opcoes")), "203.0.113.1")).andExpect(status().isForbidden());
    }

    // --- D-107 e D-109 ----------------------------------------------------------------------------------------------

    @Test
    void d107_entra_eOContadorAvanca_eAssinaturaRepetidaERecusada() throws Exception {
        var aut = new AutenticadorFalso(1L);
        cadastrarPrimeiro(aut);

        aut.contador = 5L;
        var r = entrar(aut, "203.0.113.5");
        assertThat(r.getStatus()).isEqualTo(204);
        assertThat(r.getCookie(SessaoAcessoFilter.COOKIE)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT contador FROM passkey", Long.class)).isEqualTo(5L);

        // Contador que não avança: assinatura clonada.
        var clonada = entrar(aut, "203.0.113.5");
        assertThat(clonada.getStatus()).isEqualTo(401);
        assertThat(clonada.getContentAsString()).contains("NAO_FOI_POSSIVEL_ENTRAR");
        aut.contador = 3L;
        assertThat(entrar(aut, "203.0.113.5").getStatus()).isEqualTo(401);
    }

    @Test
    void d107_passkeyQueNaoConta_entraSempreComZero() throws Exception {
        var aut = new AutenticadorFalso(null);
        cadastrarPrimeiro(aut);
        assertThat(entrar(aut, "203.0.113.5").getStatus()).isEqualTo(204);
        assertThat(entrar(aut, "203.0.113.5").getStatus()).isEqualTo(204);
    }

    @Test
    void d107_oBancoRecusaContadorQueRegride() throws Exception {
        cadastrarPrimeiro(new AutenticadorFalso(7L));
        assertThatThrownBy(() -> jdbc.update("UPDATE passkey SET contador = 7")).hasMessageContaining("D-107");
        assertThatThrownBy(() -> jdbc.update("UPDATE passkey SET contador = 6")).hasMessageContaining("D-107");
        jdbc.update("UPDATE passkey SET contador = 8");
    }

    @Test
    void d107_passkeyDesconhecidoNaoEntra() throws Exception {
        cadastrarPrimeiro(new AutenticadorFalso(1L));
        var estranho = new AutenticadorFalso(9L);
        // Só para o autenticador saber o userHandle do titular: o passkey dele nunca foi cadastrado no servidor.
        var handle = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(PasskeyRepositorio.HANDLE.getBytes());
        estranho.cadastrar("{\"pedidoId\":\"x\",\"opcoes\":{\"publicKey\":{\"rp\":{\"id\":\"eagora.tail0000.ts.net\"},"
            + "\"user\":{\"id\":\"" + handle + "\"},\"challenge\":\"AA\"}}}", ORIGEM);
        assertThat(entrar(estranho, "203.0.113.5").getStatus()).isEqualTo(401);
    }

    @Test
    void d109_cookieComOsAtributos_eOBancoGuardaSoOResumo() throws Exception {
        var sessao = cadastrarPrimeiro(new AutenticadorFalso(1L));
        assertThat(sessao.isHttpOnly()).isTrue();
        assertThat(sessao.getSecure()).isTrue();
        assertThat(sessao.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(sessao.getMaxAge()).isEqualTo((int) Duration.ofDays(30).toSeconds());
        var resumo = jdbc.queryForObject("SELECT resumo FROM sessao_acesso", byte[].class);
        assertThat(resumo).hasSize(32).isEqualTo(Segredo.resumo(sessao.getValue()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessao_acesso WHERE resumo = ?", Integer.class, sessao.getValue().getBytes())).isZero();

        mvc.perform(com(get("/api/acesso/estado"), sessao)).andExpect(jsonPath("$.logado").value(true));
        mvc.perform(com(get("/api/acesso/passkeys"), sessao)).andExpect(status().isOk()).andExpect(jsonPath("$[0].esteAparelho").value(true));
    }

    @Test
    void d109_expiraEm30DiasSemUso_eRenovaComUso() throws Exception {
        var sessao = cadastrarPrimeiro(new AutenticadorFalso(1L));
        RELOGIO.avancar(Duration.ofDays(29));
        var renovada = mvc.perform(com(get("/api/acesso/codigos"), sessao)).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(renovada.getCookie(SessaoAcessoFilter.COOKIE)).isNotNull(); // passou de 1 h: manda o cookie de novo
        RELOGIO.avancar(Duration.ofDays(29));
        mvc.perform(com(get("/api/acesso/codigos"), sessao)).andExpect(status().isOk());
        RELOGIO.avancar(Duration.ofDays(30).plusSeconds(1));
        mvc.perform(com(get("/api/acesso/codigos"), sessao)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("SESSAO_AUSENTE"));
    }

    @Test
    void d109_naoRenovaACadaPedido() throws Exception {
        var sessao = cadastrarPrimeiro(new AutenticadorFalso(1L));
        RELOGIO.avancar(Duration.ofMinutes(30));
        var r = mvc.perform(com(get("/api/acesso/codigos"), sessao)).andReturn().getResponse();
        assertThat(r.getCookie(SessaoAcessoFilter.COOKIE)).isNull();
    }

    @Test
    void d109_sair_eSairDeTodos() throws Exception {
        var aut = new AutenticadorFalso(1L);
        var primeira = cadastrarPrimeiro(aut);
        aut.contador = 2L;
        var segunda = entrar(aut, "203.0.113.5").getCookie(SessaoAcessoFilter.COOKIE);

        mvc.perform(com(post("/api/acesso/sair"), segunda)).andExpect(status().isNoContent());
        mvc.perform(com(get("/api/acesso/codigos"), segunda)).andExpect(status().isUnauthorized());
        mvc.perform(com(get("/api/acesso/codigos"), primeira)).andExpect(status().isOk());

        aut.contador = 3L;
        var terceira = entrar(aut, "203.0.113.5").getCookie(SessaoAcessoFilter.COOKIE);
        mvc.perform(com(post("/api/acesso/sair-de-todos"), terceira)).andExpect(status().isNoContent());
        mvc.perform(com(get("/api/acesso/codigos"), primeira)).andExpect(status().isUnauthorized());
        mvc.perform(com(get("/api/acesso/codigos"), terceira)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessao_acesso", Integer.class)).isEqualTo(3); // nada apagado (D-18)
    }

    @Test
    void d109_pedidoQueMudaDado_deOutraOrigem_eRecusado() throws Exception {
        var sessao = cadastrarPrimeiro(new AutenticadorFalso(1L));
        mvc.perform(post("/api/acesso/codigos").header("X-Forwarded-Host", HOST).header("X-Forwarded-Proto", "https")
                .header("Origin", "https://outro.example").cookie(sessao))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ORIGEM_RECUSADA"));
        mvc.perform(post("/api/acesso/codigos").header("X-Forwarded-Host", HOST).header("X-Forwarded-Proto", "https").cookie(sessao))
            .andExpect(status().isForbidden());
        mvc.perform(com(post("/api/acesso/codigos"), sessao)).andExpect(status().isOk());
    }

    // --- D-110 ------------------------------------------------------------------------------------------------------

    @Test
    void d110_oitoCodigos_cadaUmUmaVez_eNovosInvalidamOsVelhos() throws Exception {
        cadastrarPrimeiro(new AutenticadorFalso(1L));
        assertThat(ultimosCodigos).hasSize(8);
        var primeiro = ultimosCodigos.get(0).asString();
        assertThat(primeiro).matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigo_recuperacao WHERE resumo = ?", Integer.class,
            (Object) primeiro.getBytes())).isZero();

        var r = mvc.perform(publico(post("/api/acesso/recuperar"), "203.0.113.7").contentType(MediaType.APPLICATION_JSON)
            .content("{\"codigo\":\"" + primeiro.toLowerCase().replace("-", " ") + "\"}")).andExpect(status().isNoContent()).andReturn().getResponse();
        var sessao = r.getCookie(SessaoAcessoFilter.COOKIE);
        mvc.perform(publico(post("/api/acesso/recuperar"), "203.0.113.7").contentType(MediaType.APPLICATION_JSON)
            .content("{\"codigo\":\"" + primeiro + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(com(get("/api/acesso/codigos"), sessao)).andExpect(jsonPath("$.restantes").value(7));

        var novos = mvc.perform(com(post("/api/acesso/codigos"), sessao)).andExpect(status().isOk()).andExpect(jsonPath("$.codigos.length()").value(8))
            .andReturn().getResponse().getContentAsString();
        assertThat(novos).doesNotContain(ultimosCodigos.get(1).asString());
        mvc.perform(publico(post("/api/acesso/recuperar"), "203.0.113.8").contentType(MediaType.APPLICATION_JSON)
            .content("{\"codigo\":\"" + ultimosCodigos.get(1).asString() + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(com(get("/api/acesso/codigos"), sessao)).andExpect(jsonPath("$.restantes").value(8));
    }

    @Test
    void d110_d111_codigoAbreSessao_queCadastraOPasskeyDoAparelhoNovo() throws Exception {
        cadastrarPrimeiro(new AutenticadorFalso(1L));
        var sessao = mvc.perform(publico(post("/api/acesso/recuperar"), "203.0.113.7").contentType(MediaType.APPLICATION_JSON)
            .content("{\"codigo\":\"" + ultimosCodigos.get(2).asString() + "\"}")).andReturn().getResponse().getCookie(SessaoAcessoFilter.COOKIE);

        var novo = new AutenticadorFalso(1L);
        var opcoes = mvc.perform(com(post("/api/acesso/passkeys/opcoes"), sessao)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(com(post("/api/acesso/passkeys"), sessao).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pedidoId\":\"" + AutenticadorFalso.pedidoId(opcoes) + "\",\"credencial\":" + novo.cadastrar(opcoes, ORIGEM) + ",\"nome\":\"Tablet\"}"))
            .andExpect(status().isOk());
        mvc.perform(com(get("/api/acesso/passkeys"), sessao)).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].nome").value("Tablet")).andExpect(jsonPath("$[1].esteAparelho").value(true));
        novo.contador = 2L;
        assertThat(entrar(novo, "203.0.113.7").getStatus()).isEqualTo(204);
    }

    // --- D-111 ------------------------------------------------------------------------------------------------------

    @Test
    void d111_cadastrarOutroExigeSessao() throws Exception {
        cadastrarPrimeiro(new AutenticadorFalso(1L));
        mvc.perform(publico(post("/api/acesso/passkeys/opcoes"), "203.0.113.7")).andExpect(status().isUnauthorized());
        mvc.perform(tailnet(post("/api/acesso/passkeys/opcoes"))).andExpect(status().isUnauthorized());
    }

    @Test
    void d111_oUltimoPasskeyNaoSeRemove_noServicoENoBanco() throws Exception {
        var sessao = cadastrarPrimeiro(new AutenticadorFalso(1L));
        mvc.perform(com(delete("/api/acesso/passkeys/1"), sessao)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("ULTIMO_PASSKEY"));
        assertThatThrownBy(() -> jdbc.update("UPDATE passkey SET removido_em = now()")).hasMessageContaining("D-111");

        var outro = new AutenticadorFalso(1L);
        var opcoes = mvc.perform(com(post("/api/acesso/passkeys/opcoes"), sessao)).andReturn().getResponse().getContentAsString();
        mvc.perform(com(post("/api/acesso/passkeys"), sessao).contentType(MediaType.APPLICATION_JSON)
            .content("{\"pedidoId\":\"" + AutenticadorFalso.pedidoId(opcoes) + "\",\"credencial\":" + outro.cadastrar(opcoes, ORIGEM) + "}"));
        mvc.perform(com(delete("/api/acesso/passkeys/1"), sessao)).andExpect(status().isNoContent());
        mvc.perform(com(get("/api/acesso/passkeys"), sessao)).andExpect(jsonPath("$.length()").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM passkey", Integer.class)).isEqualTo(2); // marcado, não apagado
        mvc.perform(com(delete("/api/acesso/passkeys/2"), sessao)).andExpect(status().isConflict());
    }

    // --- Host ----------------------------------------------------------------------------------------------------------

    @Test
    void passkeySoNoEnderecoDoTailscale() throws Exception {
        mvc.perform(post("/api/acesso/entrar/opcoes").header("X-Forwarded-Host", "192.168.0.10:8443").header("X-Forwarded-Proto", "https"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.codigo").value("HOST_NAO_PERMITIDO"));
    }
}
