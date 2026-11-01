package br.com.estudos.shared.exception;

import java.time.Instant;

/** Freio de tentativas (D-112). HTTP 429; diz quando volta. */
public class MuitasTentativasException extends DominioException {

    private final Instant ate;

    public MuitasTentativasException(String mensagem, String codigo, Instant ate) {
        super(mensagem, codigo);
        this.ate = ate;
    }

    public Instant getAte() {
        return ate;
    }
}
