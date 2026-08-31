package br.com.estudos.shared.exception;

import java.net.URI;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import br.com.estudos.shared.web.RequestIdFilter;

/**
 * Único ponto de tradução de exceção de domínio para application/problem+json
 * (ADR-026). Nenhum controller trata exceção — tudo passa por aqui.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI TYPE_REGRA_DE_NEGOCIO = URI.create("https://estudos.local/errors/regra-de-negocio");

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail tratarNaoEncontrado(NotFoundException e) {
        return montar(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail tratarConflito(ConflictException e) {
        return montar(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail tratarValidacao(ValidationException e) {
        return montar(HttpStatus.UNPROCESSABLE_ENTITY, e);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail tratarRegraDeNegocio(BusinessRuleException e) {
        return montar(HttpStatus.CONFLICT, e);
    }

    private ProblemDetail montar(HttpStatus status, DominioException e) {
        var problema = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problema.setType(TYPE_REGRA_DE_NEGOCIO);
        problema.setProperty("codigo", e.getCodigo());
        problema.setProperty("campo", e.getCampo());
        problema.setProperty("requestId", MDC.get(RequestIdFilter.REQUEST_ID));
        return problema;
    }
}
