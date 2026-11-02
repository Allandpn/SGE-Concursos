package br.com.estudos.pesobanca;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.com.estudos.shared.enums.TipoPeso;

/** As contas puras do peso pela banca (01_DOMINIO §3.23; D-145, D-146; docs/SPRINT-22-PESO-PELA-BANCA.md §2). */
public final class PesoBanca {

    private PesoBanca() {}

    /** Um assunto da disciplina: o peso do edital e quantas questões da banca o cobraram. */
    public record Linha(long assuntoId, TipoPeso pesoEdital, int questoes) {}

    /** D-145: o nome sem acento, sem caixa, só letras e dígitos; CESPE e Cebraspe são a mesma banca. */
    public static String normalizar(String banca) {
        if (banca == null) return "";
        var s = Normalizer.normalize(banca, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
        return s.contains("cespe") || s.contains("cebraspe") ? "cebraspe" : s;
    }

    public static boolean mesmaBanca(String a, String b) {
        var na = normalizar(a);
        return !na.isEmpty() && na.equals(normalizar(b));
    }

    /**
     * D-146: com t = ⌈2(n−1)/3⌉, sobe um degrau quem tem mais questões que pelo menos t dos outros; desce quem tem menos que pelo
     * menos t. A base é sempre o peso do edital. Com menos de dois assuntos, nada.
     */
    public static Map<Long, TipoPeso> propor(List<Linha> linhas) {
        var propostas = new HashMap<Long, TipoPeso>();
        var n = linhas.size();
        if (n < 2) {
            linhas.forEach(l -> propostas.put(l.assuntoId(), l.pesoEdital()));
            return propostas;
        }
        var t = (2 * (n - 1) + 2) / 3; // ⌈2(n−1)/3⌉
        for (var l : linhas) {
            var menos = linhas.stream().filter(o -> o != l && o.questoes() < l.questoes()).count();
            var mais = linhas.stream().filter(o -> o != l && o.questoes() > l.questoes()).count();
            var peso = l.pesoEdital();
            if (menos >= t) peso = subir(peso);
            else if (mais >= t) peso = descer(peso);
            propostas.put(l.assuntoId(), peso);
        }
        return propostas;
    }

    static TipoPeso subir(TipoPeso p) {
        return p == TipoPeso.BAIXO ? TipoPeso.MEDIO : TipoPeso.ALTO;
    }

    static TipoPeso descer(TipoPeso p) {
        return p == TipoPeso.ALTO ? TipoPeso.MEDIO : TipoPeso.BAIXO;
    }
}
