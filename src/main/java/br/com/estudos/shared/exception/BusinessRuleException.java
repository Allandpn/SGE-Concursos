package br.com.estudos.shared.exception;

/** Operação viola uma regra de negócio que não é nem 404 nem validação de campo. HTTP 409. */
public class BusinessRuleException extends DominioException {

    public BusinessRuleException(String mensagem, String codigo) {
        super(mensagem, codigo);
    }
}
