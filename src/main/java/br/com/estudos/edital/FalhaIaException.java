package br.com.estudos.edital;

/**
 * Falha numa etapa da análise (IA fora, resposta ilegível). Nunca chega à
 * API como exceção: o processamento a transforma em status FALHOU com a
 * mensagem, e a tela oferece "tentar de novo" (docs/SPRINT-18-CONCURSOS.md §4.1).
 */
public class FalhaIaException extends RuntimeException {

    public FalhaIaException(String mensagem) {
        super(mensagem);
    }
}
