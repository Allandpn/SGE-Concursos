package br.com.estudos.shared.exception;

/** Pedido entendido e recusado por de onde veio (D-108, D-109, D-113). HTTP 403. */
public class ProibidoException extends DominioException {

    public ProibidoException(String mensagem, String codigo) {
        super(mensagem, codigo);
    }
}
