package br.com.estudos.metrica;

import br.com.estudos.shared.enums.FormatoBanca;

/**
 * Projeção de uma sessão de QUESTOES para M-1 (docs/SPRINT-7-METRICAS.md
 * §4.1) — evita hidratar Sessao/Assunto/Disciplina inteiros só pra somar
 * quatro números.
 */
public interface AcertoLinha {
    Long getDisciplinaId();
    FormatoBanca getFormato();
    Integer getQuestoesCorretas();
    Integer getQuestoesTotal();
}
