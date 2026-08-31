package br.com.estudos.shared.exception;

/**
 * Base das exceções de domínio traduzidas em ProblemDetail pelo
 * GlobalExceptionHandler (ADR-026). Toda regra que rejeita uma operação
 * lança uma subclasse desta, nunca a exceção crua do driver/JPA.
 */
public abstract class DominioException extends RuntimeException {

    private final String codigo;
    private final String campo;

    protected DominioException(String mensagem, String codigo) {
        this(mensagem, codigo, null);
    }

    protected DominioException(String mensagem, String codigo, String campo) {
        super(mensagem);
        this.codigo = codigo;
        this.campo = campo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getCampo() {
        return campo;
    }
}
