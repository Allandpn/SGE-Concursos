package br.com.estudos.acesso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** Valores aleatórios e o resumo que o banco guarda no lugar deles (D-109, D-110). */
final class Segredo {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    /** Base32 sem 0/O, 1/I/L: o código de recuperação é lido e digitado por gente. */
    private static final char[] ALFABETO_CODIGO = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private Segredo() {}

    static String tokenDeSessao() {
        var bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String idDePedido() {
        var bytes = new byte[18];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** `XXXX-XXXX-XXXX`, 12 caracteres de um alfabeto de 31 (~59 bits). */
    static String codigoDeRecuperacao() {
        var texto = new StringBuilder(14);
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                texto.append('-');
            }
            texto.append(ALFABETO_CODIGO[ALEATORIO.nextInt(ALFABETO_CODIGO.length)]);
        }
        return texto.toString();
    }

    /** Sem hífen nem espaço, maiúsculo: o mesmo código digitado de jeitos diferentes tem o mesmo resumo. */
    static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
    }

    static byte[] resumo(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
