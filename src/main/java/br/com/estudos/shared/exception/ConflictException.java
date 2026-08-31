package br.com.estudos.shared.exception;

/** Operação colide com um recurso já existente. HTTP 409. */
public class ConflictException extends DominioException {

    public ConflictException(String mensagem, String codigo) {
        super(mensagem, codigo);
    }
}
