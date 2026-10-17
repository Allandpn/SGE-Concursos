package br.com.estudos.edital;

/**
 * Falha numa chamada de IA (fora do ar, resposta ilegível, assinatura parada). Na
 * análise de edital nunca chega à API como exceção: o processamento a transforma em
 * status FALHOU com a mensagem, e a tela oferece "tentar de novo"
 * (docs/SPRINT-18-CONCURSOS.md §4.1). `assinaturaParou` marca a falha da assinatura do
 * Claude que pede a decisão do usuário (ADR-045).
 */
public class FalhaIaException extends RuntimeException {

    private final boolean assinaturaParou;

    public FalhaIaException(String mensagem) {
        this(mensagem, false);
    }

    public FalhaIaException(String mensagem, boolean assinaturaParou) {
        super(mensagem);
        this.assinaturaParou = assinaturaParou;
    }

    public boolean assinaturaParou() {
        return assinaturaParou;
    }
}
