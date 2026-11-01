package br.com.estudos.shared.exception;

/** Sem sessão, ou credencial que não valeu (D-106, D-107). HTTP 401. A mensagem nunca diz o que existe. */
public class NaoAutorizadoException extends DominioException {

    public NaoAutorizadoException(String mensagem, String codigo) {
        super(mensagem, codigo);
    }
}
