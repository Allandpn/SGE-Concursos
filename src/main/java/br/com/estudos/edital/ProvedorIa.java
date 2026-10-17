package br.com.estudos.edital;

/** De onde vem a IA em uso (ADR-045). Só o usuário troca. */
public enum ProvedorIa {
    /** Assinatura do Claude, pelo Claude Code no contêiner do app. */
    CLAUDE,
    /** OpenRouter, com chave e crédito próprios (ADR-043). */
    OPENROUTER
}
