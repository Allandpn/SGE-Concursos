package br.com.estudos.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * D-106, teste estrutural (03_INVARIANTES §3): percorre toda rota mapeada no Spring e prova que, fora da lista aberta,
 * nenhuma responde sem sessão. Rota nova entra aqui sozinha: nasce fechada, ou o teste falha.
 */
class AcessoRotasTest extends AcessoTestBase {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapeamento;

    @Test
    void d106_todaRotaForaDaListaAberta_respondeSemSessaoSo401() throws Exception {
        var abertasVistas = new ArrayList<String>();
        var fechadas = 0;
        for (var info : mapeamento.getHandlerMethods().keySet()) {
            var metodos = info.getMethodsCondition().getMethods().isEmpty() ? List.of(RequestMethod.GET) : info.getMethodsCondition().getMethods();
            for (var padrao : info.getPatternValues()) {
                if (padrao.startsWith("/internal/") || padrao.equals("/error")) {
                    continue; // token próprio (D-113) e o despacho de erro do Spring
                }
                var caminho = padrao.replaceAll("\\{[^}]+}", "1").replace("/**", "/x");
                for (var metodo : metodos) {
                    if (SessaoAcessoFilter.aberta(metodo.name(), caminho)) {
                        abertasVistas.add(metodo + " " + caminho);
                        continue;
                    }
                    var r = mvc.perform(publico(request(HttpMethod.valueOf(metodo.name()), caminho), "203.0.113.20")).andReturn().getResponse();
                    assertThat(r.getStatus()).as(metodo + " " + caminho).isEqualTo(401);
                    assertThat(r.getContentAsString()).as(metodo + " " + caminho).contains("SESSAO_AUSENTE");
                    fechadas++;
                }
            }
        }
        assertThat(fechadas).isGreaterThan(100);
        assertThat(abertasVistas).containsExactlyInAnyOrder(
            "GET /api/acesso/estado", "POST /api/acesso/primeiro/opcoes", "POST /api/acesso/primeiro",
            "POST /api/acesso/entrar/opcoes", "POST /api/acesso/entrar", "POST /api/acesso/recuperar");
    }

    @Test
    void d106_telaSemSessao_levaAPorta_eDadoEstaticoFechado() throws Exception {
        for (var tela : List.of("/", "/index.html", "/js/pages.js", "/js/router.js", "/qualquer-coisa")) {
            var r = mvc.perform(publico(get(tela), "203.0.113.20")).andReturn().getResponse();
            assertThat(r.getStatus()).as(tela).isEqualTo(302);
            assertThat(r.getHeader("Location")).isEqualTo("/entrar.html");
        }
        for (var dado : List.of("/material/blocos/x/segmento-01.pdf", "/v3/api-docs", "/swagger-ui/index.html", "/actuator/info")) {
            assertThat(mvc.perform(publico(get(dado), "203.0.113.20")).andReturn().getResponse().getStatus()).as(dado).isEqualTo(401);
        }
    }

    @Test
    void d106_aPortaEACascaSemDadoAbrem() throws Exception {
        for (var aberta : List.of("/entrar.html", "/js/entrar.js", "/js/api.js", "/manifest.webmanifest", "/sw.js", "/css/styles.css",
                "/vendor/alpinejs-3.14.3.min.js", "/icons/icon.svg", "/actuator/health")) {
            assertThat(mvc.perform(publico(get(aberta), "203.0.113.20")).andReturn().getResponse().getStatus()).as(aberta).isEqualTo(200);
        }
    }

    @Test
    void d113_rotaInternaRecusaPedidoDaInternet_mesmoComOToken() throws Exception {
        var r = mvc.perform(publico(get("/internal/editais/pendentes"), "203.0.113.20").header("X-Internal-Token", "token-de-teste"))
            .andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getContentAsString()).contains("ROTA_INTERNA_PUBLICA");
        // Sem a marca do Funnel (o planejamento, na rede do compose), o token continua valendo.
        var interno = mvc.perform(get("/internal/editais/pendentes").header("X-Internal-Token", "token-de-teste")).andReturn().getResponse();
        assertThat(interno.getStatus()).isNotIn(401, 403);
        assertThat(mvc.perform(get("/internal/editais/pendentes")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
