package br.com.estudos.shared.web;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * `/internal/**` só para o sistema de planejamento, no mesmo servidor (ADR-044,
 * docs/SPRINT-19-MATERIAL-AUTOMATICO.md §4). O resto da API confia na Tailscale
 * (ADR-015); estas rotas gastam crédito de IA na conta do usuário, então exigem
 * também o token compartilhado entre os contêineres. Sem token configurado no
 * servidor, ficam desligadas.
 */
@Component
public class TokenInternoFilter extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Internal-Token";

    private final String token;
    private final JsonMapper jsonMapper;

    public TokenInternoFilter(@Value("${estudos.interno.token:}") String token, JsonMapper jsonMapper) {
        this.token = token;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (token == null || token.isBlank()) {
            recusar(response, HttpStatus.SERVICE_UNAVAILABLE, "Rotas internas desligadas: defina INTERNAL_TOKEN no servidor.", "INTERNO_DESLIGADO");
            return;
        }
        var recebido = request.getHeader(CABECALHO);
        // Comparação em tempo constante: não deixa medir o token pelo tempo de resposta.
        if (recebido == null || !MessageDigest.isEqual(
                recebido.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            recusar(response, HttpStatus.UNAUTHORIZED, "Token interno ausente ou inválido.", "TOKEN_INVALIDO");
            return;
        }
        chain.doFilter(request, response);
    }

    private void recusar(HttpServletResponse response, HttpStatus status, String detalhe, String codigo) throws IOException {
        var problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(URI.create("https://estudos.local/errors/regra-de-negocio"));
        problema.setProperty("codigo", codigo);
        problema.setProperty("requestId", MDC.get(RequestIdFilter.REQUEST_ID));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(problema));
    }
}
