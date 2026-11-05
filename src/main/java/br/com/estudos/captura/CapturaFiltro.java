package br.com.estudos.captura;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ADR-063: a porta da captura. Só {@code POST /captura/qconcursos/*}, JSON, até 64 KB, com {@code Authorization: Bearer <chave>}
 * de uma chave que vale (D-170). O filtro de sessão não trata estas rotas; nenhuma outra rota aceita a chave.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class CapturaFiltro extends OncePerRequestFilter {

    public static final String PREFIXO = "/captura/qconcursos/";
    static final String ATRIBUTO = CapturaFiltro.class.getName() + ".chave";
    static final long LIMITE = 64 * 1024;

    private final CapturaChaveService chaves;

    public CapturaFiltro(CapturaChaveService chaves) {
        this.chaves = chaves;
    }

    /** As rotas da captura: as que o filtro de sessão deixa passar. */
    public static boolean ehCaptura(HttpServletRequest pedido) {
        return pedido.getRequestURI().startsWith(PREFIXO);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest pedido) {
        return !ehCaptura(pedido);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        if ("OPTIONS".equals(pedido.getMethod()) || !"POST".equals(pedido.getMethod())) {
            recusar(resposta, 405, "Só envio.", "CAPTURA_SO_ENVIO");
            return;
        }
        var tamanho = pedido.getContentLengthLong();
        if (tamanho < 0) {
            recusar(resposta, 411, "Envie o tamanho do corpo.", "CAPTURA_SEM_TAMANHO");
            return;
        }
        if (tamanho > LIMITE) {
            recusar(resposta, 413, "Envio grande demais.", "CAPTURA_GRANDE");
            return;
        }
        var tipo = pedido.getContentType();
        if (tipo == null || !tipo.toLowerCase().startsWith("application/json")) {
            recusar(resposta, 415, "Envie JSON.", "CAPTURA_SEM_JSON");
            return;
        }
        var cabecalho = pedido.getHeader(HttpHeaders.AUTHORIZATION);
        var chave = cabecalho != null && cabecalho.startsWith("Bearer ") ? cabecalho.substring(7).trim() : null;
        var id = chaves.autenticar(chave);
        if (id.isEmpty()) {
            recusar(resposta, 401, "Chave de envio inválida ou revogada.", "CAPTURA_CHAVE_RECUSADA");
            return;
        }
        pedido.setAttribute(ATRIBUTO, id.getAsLong());
        cadeia.doFilter(pedido, resposta);
    }

    private static void recusar(HttpServletResponse resposta, int status, String mensagem, String codigo) throws IOException {
        resposta.setStatus(status);
        resposta.setContentType("application/json");
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        resposta.getWriter().write("{\"codigo\":\"" + codigo + "\",\"mensagem\":\"" + mensagem + "\"}");
    }
}
