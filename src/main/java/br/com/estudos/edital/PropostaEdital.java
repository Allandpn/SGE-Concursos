package br.com.estudos.edital;

import java.util.List;

import br.com.estudos.shared.enums.TipoPeso;

/**
 * O que a análise propõe para o cargo escolhido (01_DOMINIO §3.9), já com
 * o peso calculado pelo servidor (D-61) e as correspondências validadas
 * (D-63). Guardada como JSON na análise; nada disto é cadastro (D-62).
 */
public record PropostaEdital(List<Disciplina> disciplinas) {

    public record Disciplina(
        String nome,
        Integer questoes,
        TipoPeso peso,
        boolean pesoDeterminado,
        Long disciplinaExistenteId,
        List<Assunto> assuntos) {}

    public record Assunto(String nome, Long assuntoExistenteId) {}
}
