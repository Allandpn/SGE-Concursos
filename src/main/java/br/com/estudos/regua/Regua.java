package br.com.estudos.regua;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * D-136 (docs/SPRINT-23-REGISTRO-E-PROVA-REAL.md §2): a nota em pontos do edital, derivada da régua vigente (D-135).
 * Por disciplina, certas × pontos − (errada anula certa ? erradas × pontos : 0); a global é a soma, nunca abaixo de zero.
 * Função pura; nada disso é guardado (D-16).
 */
public final class Regua {

    public record Linha(Long disciplinaId, String disciplina, int certas, int erradas, int brancos, BigDecimal pontosPorQuestao) {}

    public record PontosDisciplina(Long disciplinaId, String disciplina, int certas, int erradas, int brancos, BigDecimal pontos,
                                   BigDecimal maximo) {}

    public record Nota(List<PontosDisciplina> porDisciplina, BigDecimal pontos, BigDecimal maximo, BigDecimal notaCorte,
                       BigDecimal diferenca, boolean erradaAnulaCerta) {}

    private Regua() {}

    public static Nota pontuar(List<Linha> linhas, boolean erradaAnulaCerta, BigDecimal notaCorte) {
        var porDisciplina = linhas.stream().map(l -> {
            var p = l.pontosPorQuestao() == null ? BigDecimal.ONE : l.pontosPorQuestao();
            var pontos = p.multiply(BigDecimal.valueOf(l.certas()));
            if (erradaAnulaCerta) {
                pontos = pontos.subtract(p.multiply(BigDecimal.valueOf(l.erradas())));
            }
            var maximo = p.multiply(BigDecimal.valueOf((long) l.certas() + l.erradas() + l.brancos()));
            return new PontosDisciplina(l.disciplinaId(), l.disciplina(), l.certas(), l.erradas(), l.brancos(), escala(pontos), escala(maximo));
        }).toList();
        var total = porDisciplina.stream().map(PontosDisciplina::pontos).reduce(BigDecimal.ZERO, BigDecimal::add).max(BigDecimal.ZERO);
        var maximo = porDisciplina.stream().map(PontosDisciplina::maximo).reduce(BigDecimal.ZERO, BigDecimal::add);
        var diferenca = notaCorte == null ? null : escala(total.subtract(notaCorte));
        return new Nota(porDisciplina, escala(total), escala(maximo), notaCorte == null ? null : escala(notaCorte), diferenca, erradaAnulaCerta);
    }

    private static BigDecimal escala(BigDecimal v) {
        var r = v.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return r.signum() == 0 ? BigDecimal.ZERO : r; // "0.00" vira "0", sem notação científica
    }
}
