package br.com.estudos.shared.exception;

import java.net.URI;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    /**
     * ADR-035 — Bean Validation de forma do request (`@NotNull`/`@NotBlank`
     * etc.), não de regra de domínio. O `codigo` vem da própria mensagem da
     * anotação (`message = "ORDEM_OBRIGATORIA"`), mesma convenção dos
     * `codigo` de {@link ValidationException}. Só o primeiro erro de campo
     * vira resposta — a API já devolve um erro por vez em todo o resto.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarCampoInvalido(MethodArgumentNotValidException e) {
        var erro = e.getBindingResult().getFieldErrors().get(0);
        return montar(HttpStatus.UNPROCESSABLE_ENTITY, "Campo inválido: " + erro.getField(), erro.getDefaultMessage(), erro.getField());
    }

    private ProblemDetail montar(HttpStatus status, DominioException e) {
        return montar(status, e.getMessage(), e.getCodigo(), e.getCampo());
    }

    private ProblemDetail montar(HttpStatus status, String detalhe, String codigo, String campo) {
        var problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setType(TYPE_REGRA_DE_NEGOCIO);
        problema.setProperty("codigo", codigo);
        problema.setProperty("campo", campo);
        problema.setProperty("requestId", MDC.get(RequestIdFilter.REQUEST_ID));
        return problema;
    }
}
