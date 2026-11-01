package br.com.estudos.acesso;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

/**
 * De onde veio o pedido (docs/SPRINT-30-LOGIN.md §4; D-108, D-113).
 *
 * O `tailscale funnel` marca com {@link #MARCA_FUNNEL} todo pedido vindo da internet pública; o `tailscale serve`
 * põe {@link #IDENTIDADE_TAILNET} nos pedidos de um usuário da tailnet (e apaga o cabeçalho se o cliente o mandar).
 * O primeiro cadastro exige a prova positiva (identidade, sem a marca): um caminho que chegue sem o Tailscale não
 * parece privado.
 */
public final class OrigemDoPedido {

    public static final String MARCA_FUNNEL = "Tailscale-Funnel-Request";
    public static final String IDENTIDADE_TAILNET = "Tailscale-User-Login";

    private OrigemDoPedido() {}

    /** Veio da internet pública (Funnel). */
    public static boolean publica(HttpServletRequest pedido) {
        return pedido.getHeader(MARCA_FUNNEL) != null;
    }

    /** Veio de um usuário da tailnet, pelo `tailscale serve`, e não da internet (D-108). */
    public static boolean tailnet(HttpServletRequest pedido) {
        var identidade = pedido.getHeader(IDENTIDADE_TAILNET);
        return !publica(pedido) && identidade != null && !identidade.isBlank();
    }

    /** O host como o navegador o vê, sem a porta. */
    public static String host(HttpServletRequest pedido) {
        var hostPorta = hostComPorta(pedido);
        if (hostPorta.startsWith("[")) {
            var fim = hostPorta.indexOf(']');
            return hostPorta.substring(0, fim + 1).toLowerCase(Locale.ROOT);
        }
        var dois = hostPorta.indexOf(':');
        return (dois < 0 ? hostPorta : hostPorta.substring(0, dois)).toLowerCase(Locale.ROOT);
    }

    /** A origem do app como o navegador a vê (`https://no.tailnet.ts.net:8443`). */
    public static String origem(HttpServletRequest pedido) {
        var proto = primeiro(pedido.getHeader("X-Forwarded-Proto"));
        if (proto == null) {
            proto = pedido.getScheme();
        }
        return proto.toLowerCase(Locale.ROOT) + "://" + hostComPorta(pedido).toLowerCase(Locale.ROOT);
    }

    /** O endereço de quem pediu: o primeiro de `X-Forwarded-For` (o Tailscale o põe), senão o remoto. */
    public static String cliente(HttpServletRequest pedido) {
        var encaminhado = primeiro(pedido.getHeader("X-Forwarded-For"));
        return encaminhado != null ? encaminhado : pedido.getRemoteAddr();
    }

    private static String hostComPorta(HttpServletRequest pedido) {
        var encaminhado = primeiro(pedido.getHeader("X-Forwarded-Host"));
        if (encaminhado != null) {
            return encaminhado;
        }
        var host = pedido.getHeader("Host");
        if (host != null && !host.isBlank()) {
            return host.trim();
        }
        var porta = pedido.getServerPort();
        var padrao = ("https".equals(pedido.getScheme()) && porta == 443) || ("http".equals(pedido.getScheme()) && porta == 80);
        return pedido.getServerName() + (padrao ? "" : ":" + porta);
    }

    private static String primeiro(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        var virgula = valor.indexOf(',');
        return (virgula < 0 ? valor : valor.substring(0, virgula)).trim();
    }
}
