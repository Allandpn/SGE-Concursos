package br.com.estudos.shared.exception;

/** Recurso referenciado por id não existe. HTTP 404. */
public class NotFoundException extends DominioException {

    public NotFoundException(String mensagem, String codigo) {
        super(mensagem, codigo);
    }
}
