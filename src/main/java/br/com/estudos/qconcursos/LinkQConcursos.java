package br.com.estudos.qconcursos;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * O endereço de questões do QConcursos (01_DOMINIO §3.24, D-150 a D-152; ADR-058; docs/SPRINT-36-QCONCURSOS.md §2). Função pura: os
 * nomes dos parâmetros são os que o próprio site gera, conferidos em endereços copiados do navegador pelo usuário.
 */
public final class LinkQConcursos {

    public static final String BASE = "https://www.qconcursos.com/questoes-de-concursos/questoes";

    /** Os tamanhos de página que o QConcursos aceita. */
    static final int[] POR_PAGINA = {5, 10, 15, 20};

    /** O filtro, já resolvido. Banca, formato e anos podem faltar. */
    public record Filtro(Collection<Long> disciplinas, Collection<Long> assuntos, Integer banca, String formato, Integer anoInicial,
        Integer anoFinal, int porPagina) {}

    private static final Pattern NUMERO = Pattern.compile("\\d{1,9}");

    private LinkQConcursos() {}

    public static String montar(Filtro f) {
        var partes = new ArrayList<String>();
        new LinkedHashSet<>(f.disciplinas()).forEach(d -> partes.add("discipline_ids%5B%5D=" + d));
        if (f.banca() != null) partes.add("examining_board_ids%5B%5D=" + f.banca());
        partes.add("exclude_nullified=true");
        partes.add("exclude_outdated=true");
        var modalidade = modalidade(f.formato());
        if (modalidade != null) partes.add("modality_ids%5B%5D=" + modalidade);
        partes.add("my_questions=not_resolved");
        partes.add("per_page=" + f.porPagina());
        if (f.anoInicial() != null && f.anoFinal() != null) {
            for (var ano = f.anoInicial(); ano <= f.anoFinal(); ano++) partes.add("publication_year%5B%5D=" + ano);
        }
        new LinkedHashSet<>(f.assuntos()).forEach(a -> partes.add("subject_ids%5B%5D=" + a));
        return BASE + "?" + String.join("&", partes);
    }

    /** O formato do SGE na modalidade do QConcursos: 1 múltipla escolha, 2 certo/errado. */
    static Integer modalidade(String formato) {
        if ("MULTIPLA_ESCOLHA".equals(formato)) return 1;
        if ("CERTO_ERRADO".equals(formato)) return 2;
        return null;
    }

    /** D-150: o menor tamanho de página aceito que caiba as sugeridas; 20 no máximo. */
    public static int porPagina(int sugeridas) {
        for (var p : POR_PAGINA) {
            if (p >= sugeridas) return p;
        }
        return POR_PAGINA[POR_PAGINA.length - 1];
    }

    /** D-152: máx(mínimo do lote, ⌊minutos do foco ÷ minutos por questão⌋). */
    public static int sugeridas(double focoMinutos, double minutosPorQuestao, int minimo) {
        var cabem = minutosPorQuestao > 0 ? (int) Math.floor(focoMinutos / minutosPorQuestao) : 0;
        return Math.max(minimo, cabem);
    }

    /**
     * D-151: o número da banca num endereço do QConcursos filtrado por ela (um único {@code examining_board_ids[]}), ou o próprio
     * número digitado. Vazio se não houver exatamente um.
     */
    public static Optional<Integer> numeroDaBanca(String texto) {
        if (texto == null) return Optional.empty();
        var t = texto.trim();
        if (NUMERO.matcher(t).matches()) return positivo(t);
        String query;
        try {
            query = URI.create(t.replace("[", "%5B").replace("]", "%5D").replace(" ", "%20")).getRawQuery();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (query == null) return Optional.empty();
        var achados = new ArrayList<String>();
        for (var par : query.split("&")) {
            var i = par.indexOf('=');
            if (i < 0) continue;
            var chave = URLDecoder.decode(par.substring(0, i), StandardCharsets.UTF_8);
            if (chave.equals("examining_board_ids[]") || chave.equals("examining_board_ids")) {
                achados.add(URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8).trim());
            }
        }
        var distintos = List.copyOf(new LinkedHashSet<>(achados));
        if (distintos.size() != 1 || !NUMERO.matcher(distintos.get(0)).matches()) return Optional.empty();
        return positivo(distintos.get(0));
    }

    private static Optional<Integer> positivo(String s) {
        var n = Integer.parseInt(s);
        return n > 0 ? Optional.of(n) : Optional.empty();
    }
}
