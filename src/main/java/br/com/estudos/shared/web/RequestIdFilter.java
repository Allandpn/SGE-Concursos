package br.com.estudos.shared.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Gera o requestId de correlação (03C_LOGGING §2): presente em todo log da
 * requisição via MDC, no header X-Request-Id de toda resposta e no
 * ProblemDetail de todo erro (ADR-026).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var id = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(REQUEST_ID, id);
        response.setHeader("X-Request-Id", id);
        try {
            chain.doFilter(request, response);
        } finally {
            // ThreadLocal reaproveitado pelo Tomcat entre requisições — sem isto,
            // o id de uma requisição vaza para o log da próxima (03C_LOGGING §2).
            MDC.clear();
        }
    }
}
