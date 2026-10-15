package br.com.estudos.edital;

import java.util.List;

import br.com.estudos.shared.enums.TipoPeso;

/**
 * D-61 — o peso vem do número de questões, por regra fixa, nunca da IA
 * (01_DOMINIO §3.9). Ordenadas as disciplinas com contagem declarada da
 * maior para a menor, a posição de cada uma é quantas têm mais questões que
 * ela: primeiro terço ALTO, segundo MEDIO, último BAIXO; empate tem a mesma
 * posição, logo o mesmo peso. Sem contagem: MEDIO, não determinado.
 */
public final class PesoPorQuestoes {

    public record PesoCalculado(TipoPeso peso, boolean determinado) {}

    private PesoPorQuestoes() {}

    public static List<PesoCalculado> calcular(List<Integer> questoes) {
        var declarados = questoes.stream().filter(q -> q != null && q > 0).toList();
        long n = declarados.size();
        return questoes.stream().map(q -> {
            if (q == null || q <= 0) return new PesoCalculado(TipoPeso.MEDIO, false);
            long posicao = declarados.stream().filter(outra -> outra > q).count();
            if (posicao * 3 < n) return new PesoCalculado(TipoPeso.ALTO, true);
            if (posicao * 3 < 2 * n) return new PesoCalculado(TipoPeso.MEDIO, true);
            return new PesoCalculado(TipoPeso.BAIXO, true);
        }).toList();
    }
}
