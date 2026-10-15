package br.com.estudos.shared.exception;

/** Dependência externa fora do ar ou não configurada (ADR-043: OpenRouter). HTTP 503. */
public class IndisponivelException extends DominioException {

    public IndisponivelException(String mensagem, String codigo) {
        super(mensagem, codigo);
    }
}
