package br.com.estudos.proprio;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

/**
 * O corte do PDF próprio em blocos (D-129; docs/SPRINT-33-PDF-PROPRIO.md §4): ~60 min pelo ritmo, no início de uma seção
 * quando há um entre 75% e 125% do alvo, a sobra pequena juntada ao último. Função pura, testada sozinha.
 */
final class CorteDoPdf {

    static final int ALVO_MIN = 60;

    record Bloco(int inicio, int fim) {
        int paginas() {
            return fim - inicio + 1;
        }
    }

    private CorteDoPdf() {}

    /** Páginas de 1 a {@code paginas}; {@code iniciosDeSecao} são as páginas onde uma seção começa (marcadores). */
    static List<Bloco> cortar(int paginas, int minutosPorPagina, SortedSet<Integer> iniciosDeSecao) {
        int alvo = Math.max(1, ALVO_MIN / minutosPorPagina);
        int minimo = Math.max(1, (int) Math.ceil(alvo * 0.75));
        int maximo = Math.max(minimo, (int) Math.floor(alvo * 1.25));
        var blocos = new ArrayList<Bloco>();
        int inicio = 1;
        while (inicio <= paginas) {
            int ideal = inicio + alvo - 1;
            int fim = Math.min(ideal, paginas);
            if (ideal < paginas) {
                Integer melhor = null;
                for (int s : iniciosDeSecao.subSet(inicio + minimo, inicio + maximo + 1)) {
                    if (melhor == null || Math.abs((s - 1) - ideal) < Math.abs((melhor - 1) - ideal)) {
                        melhor = s;
                    }
                }
                if (melhor != null) {
                    fim = melhor - 1;
                }
                // Sobra menor que um quarto do alvo junta-se a este bloco.
                if (paginas - fim < Math.max(1, alvo / 4)) {
                    fim = paginas;
                }
            }
            blocos.add(new Bloco(inicio, fim));
            inicio = fim + 1;
        }
        return blocos;
    }
}
