package br.com.estudos.shared.teste;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.estudos.shared.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Marca a requisição do modo de teste (ADR-061), depois do filtro de acesso (que sempre lê o banco real). Com o cookie do aparelho e
 * o schema pronto, o banco e o relógio da requisição passam a ser os do teste. O acesso e as rotas internas ficam sempre no real.
 * No teste, recusa o envio de arquivo e o reprocessamento, que escrevem fora do banco ou entregam trabalho ao planejamento.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ModoTesteFiltro extends OncePerRequestFilter {

    public static final String COOKIE = "modo_teste";

    private static final Pattern REPROCESSAR = Pattern.compile(".*/(reprocessar|refazer|preparar)(/.*)?$");

    private final ModoTesteService servico;
    private final JsonMapper json;

    public ModoTesteFiltro(ModoTesteService servico, JsonMapper json) {
        this.servico = servico;
        this.json = json;
    }

    public static boolean pedeTeste(HttpServletRequest pedido) {
        if (pedido.getCookies() == null) return false;
        for (Cookie c : pedido.getCookies()) {
            if (COOKIE.equals(c.getName()) && "1".equals(c.getValue())) return true;
        }
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        var caminho = pedido.getRequestURI();
        return caminho.startsWith("/internal/") || caminho.startsWith("/api/acesso") || !pedeTeste(pedido);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        if (!servico.preparado()) {
            cadeia.doFilter(pedido, resposta);
            return;
        }
        var caminho = pedido.getRequestURI();
        var multipart = pedido.getContentType() != null && pedido.getContentType().toLowerCase().startsWith("multipart/");
        if (!"GET".equals(pedido.getMethod())
                && ((multipart && !caminho.startsWith("/api/assuntos/importacoes")) || REPROCESSAR.matcher(caminho).matches())) {
            recusar(resposta);
            return;
        }
        ModoTeste.ligar(servico.deslocamento());
        try {
            cadeia.doFilter(pedido, resposta);
        } finally {
            ModoTeste.desligar();
        }
    }

    private void recusar(HttpServletResponse resposta) throws IOException {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "No modo de teste não dá para enviar arquivo nem reprocessar. Saia do modo de teste para fazer isso de verdade.");
        problema.setType(URI.create("https://estudos.local/errors/regra-de-negocio"));
        problema.setProperty("codigo", "MODO_TESTE_SEM_ARQUIVO");
        problema.setProperty("requestId", MDC.get(RequestIdFilter.REQUEST_ID));
        resposta.setStatus(HttpStatus.CONFLICT.value());
        resposta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.getWriter().write(json.writeValueAsString(problema));
    }
}
