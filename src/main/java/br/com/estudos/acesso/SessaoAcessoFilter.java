package br.com.estudos.acesso;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.estudos.acesso.SessaoAcessoService.Atual;
import br.com.estudos.shared.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * D-106 (docs/SPRINT-30-LOGIN.md §3.1): toda rota exige sessão, menos a lista aberta. Rota nova nasce fechada: a lista
 * é de exceções, não de proteções. Sem sessão, dado responde 401 e tela leva à porta (`/entrar.html`).
 *
 * D-109: a sessão vem do cookie {@link #COOKIE}; renova quando o último uso passou de uma hora; pedido que muda dado
 * precisa da origem do próprio app. `/internal/**` não passa por aqui: tem o token próprio e a recusa de D-113.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SessaoAcessoFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(SessaoAcessoFilter.class);

    public static final String COOKIE = "eagora_sessao";
    static final String ATRIBUTO = SessaoAcessoFilter.class.getName() + ".sessao";
    public static final String PORTA = "/entrar.html";

    /** A lista aberta (D-106). Tudo o que não está aqui exige sessão. */
    static final Set<String> ABERTAS_GET = Set.of(
        "/entrar.html", "/js/entrar.js", "/js/api.js", "/manifest.webmanifest", "/sw.js", "/actuator/health", "/api/acesso/estado");
    static final List<String> PREFIXOS_ABERTOS_GET = List.of("/vendor/", "/css/", "/icons/");
    static final Set<String> ABERTAS_POST = Set.of(
        "/api/acesso/primeiro/opcoes", "/api/acesso/primeiro", "/api/acesso/entrar/opcoes", "/api/acesso/entrar", "/api/acesso/recuperar");
    private static final Set<String> MUDAM_DADO = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final SessaoAcessoService sessoes;
    private final JsonMapper json;
    private final boolean desligado;

    public SessaoAcessoFilter(SessaoAcessoService sessoes, JsonMapper json,
                              @Value("${estudos.acesso.desligado-em-teste:false}") boolean desligado) {
        this.sessoes = sessoes;
        this.json = json;
        this.desligado = desligado;
        if (desligado) {
            LOG.warn("acesso: o login está DESLIGADO (estudos.acesso.desligado-em-teste). Isto só é aceitável em teste.");
        }
    }

    /** A sessão deste pedido, posta pelo filtro. */
    public static Optional<Atual> sessao(HttpServletRequest pedido) {
        return Optional.ofNullable((Atual) pedido.getAttribute(ATRIBUTO));
    }

    public static ResponseCookie cookie(String token) {
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(true).sameSite("Strict").path("/")
            .maxAge(SessaoAcessoService.DURACAO).build();
    }

    public static ResponseCookie cookieApagado() {
        return ResponseCookie.from(COOKIE, "").httpOnly(true).secure(true).sameSite("Strict").path("/").maxAge(0).build();
    }

    static boolean aberta(String metodo, String caminho) {
        if ("GET".equals(metodo) || "HEAD".equals(metodo)) {
            return ABERTAS_GET.contains(caminho) || PREFIXOS_ABERTOS_GET.stream().anyMatch(caminho::startsWith);
        }
        return "POST".equals(metodo) && ABERTAS_POST.contains(caminho);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        return desligado || pedido.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        var caminho = pedido.getRequestURI();
        var metodo = pedido.getMethod();
        var sessao = sessoes.validar(lerCookie(pedido));

        if (sessao.isPresent()) {
            if (MUDAM_DADO.contains(metodo) && !OrigemDoPedido.origem(pedido).equals(pedido.getHeader(HttpHeaders.ORIGIN))) {
                recusar(resposta, HttpStatus.FORBIDDEN, "Pedido de outra origem.", "ORIGEM_RECUSADA");
                return;
            }
            pedido.setAttribute(ATRIBUTO, sessao.get());
            if (sessao.get().renovada()) {
                resposta.addHeader(HttpHeaders.SET_COOKIE, cookie(lerCookie(pedido)).toString());
            }
            cadeia.doFilter(pedido, resposta);
            return;
        }
        if (aberta(metodo, caminho)) {
            cadeia.doFilter(pedido, resposta);
            return;
        }
        if (("GET".equals(metodo) || "HEAD".equals(metodo)) && !ehDado(caminho)) {
            resposta.setStatus(HttpStatus.FOUND.value());
            resposta.setHeader(HttpHeaders.LOCATION, PORTA);
            resposta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return;
        }
        recusar(resposta, HttpStatus.UNAUTHORIZED, "Entre para continuar.", "SESSAO_AUSENTE");
    }

    private static boolean ehDado(String caminho) {
        return caminho.startsWith("/api/") || caminho.startsWith("/material/") || caminho.startsWith("/v3/")
            || caminho.startsWith("/swagger-ui") || caminho.startsWith("/actuator/");
    }

    private static String lerCookie(HttpServletRequest pedido) {
        var cookies = pedido.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private void recusar(HttpServletResponse resposta, HttpStatus status, String detalhe, String codigo) throws IOException {
        var problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(URI.create("https://estudos.local/errors/regra-de-negocio"));
        problema.setProperty("codigo", codigo);
        problema.setProperty("requestId", MDC.get(RequestIdFilter.REQUEST_ID));
        resposta.setStatus(status.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.getWriter().write(json.writeValueAsString(problema));
    }
}
