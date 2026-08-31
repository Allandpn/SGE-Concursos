package br.com.estudos.shared.exception;

/** Dado recusado por regra de validação, aponta o campo. HTTP 422. */
public class ValidationException extends DominioException {

    public ValidationException(String mensagem, String codigo, String campo) {
        super(mensagem, codigo, campo);
    }
}
