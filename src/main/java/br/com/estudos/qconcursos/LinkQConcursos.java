package br.com.estudos.qconcursos;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    /** O endereço com os recortes de antes (D-150): tira anuladas, desatualizadas e resolvidas, sem outros recortes. */
    public static String montar(Filtro f) {
        return montar(f, Recortes.ANTES, null).url();
    }

    /** Um filtro aprendido pelo endereço (D-176): o tipo, o número e o nome que a pessoa deu. */
    public record Aprendido(String tipo, int numero, String nome) {}

    /** D-175: os recortes do filtro padrão (ou da exceção do concurso), além do assunto, da banca, do formato e dos anos. */
    public record Recortes(List<Integer> niveis, List<Integer> dificuldades, String ordem, boolean tirarResolvidas,
        boolean tirarAnuladas, boolean tirarDesatualizadas, boolean soComentadas, List<Aprendido> aprendidos) {
        public static final Recortes ANTES = new Recortes(List.of(), List.of(), "QC", true, true, true, false, List.of());
    }

    /** D-177: uma marca da folha; desligá-la tira estes parâmetros do endereço, só daquela vez. */
    public record Marca(String chave, String rotulo, List<String> parametros) {}

    public record Montado(String url, List<Marca> marcas) {}

    /** Os tipos aprendidos (D-176) e o nome do parâmetro de cada um no QConcursos. */
    public static final Map<String, String> PARAMETRO_APRENDIDO = Map.of(
        "CARGO", "job_ids", "FORMACAO", "knowledge_area_ids", "ATUACAO", "administrative_level_ids", "INSTITUICAO", "institute_ids");

    static final List<String> NIVEIS = List.of("", "Fundamental", "Médio", "Superior");
    static final List<String> DIFICULDADES = List.of("", "Muito fácil", "Fácil", "Médio", "Difícil", "Muito difícil");

    private record Parte(String chave, String texto) {}

    /**
     * D-175 e D-177: o endereço e as marcas que se desligam. Os parâmetros saem em ordem alfabética, como o próprio QConcursos
     * gera; {@code bancaNome} é só para o rótulo da marca.
     */
    public static Montado montar(Filtro f, Recortes r, String bancaNome) {
        var partes = new ArrayList<Parte>();
        var marcas = new LinkedHashMap<String, Marca>();
        new LinkedHashSet<>(f.disciplinas()).forEach(d -> partes.add(new Parte(null, "discipline_ids%5B%5D=" + d)));
        new LinkedHashSet<>(f.assuntos()).forEach(a -> partes.add(new Parte(null, "subject_ids%5B%5D=" + a)));
        if (f.banca() != null) {
            marca(marcas, partes, "banca", bancaNome == null ? "Banca nº " + f.banca() : bancaNome,
                List.of("examining_board_ids%5B%5D=" + f.banca()));
        }
        if (r.tirarAnuladas()) partes.add(new Parte(null, "exclude_nullified=true"));
        if (r.tirarDesatualizadas()) partes.add(new Parte(null, "exclude_outdated=true"));
        if (r.tirarResolvidas()) partes.add(new Parte(null, "my_questions=not_resolved"));
        var modalidade = modalidade(f.formato());
        if (modalidade != null) {
            marca(marcas, partes, "formato", modalidade == 1 ? "Múltipla escolha" : "Certo/errado", List.of("modality_ids%5B%5D=" + modalidade));
        }
        partes.add(new Parte(null, "per_page=" + f.porPagina()));
        if (f.anoInicial() != null && f.anoFinal() != null) {
            var anos = new ArrayList<String>();
            for (var ano = f.anoInicial(); ano <= f.anoFinal(); ano++) anos.add("publication_year%5B%5D=" + ano);
            marca(marcas, partes, "anos", f.anoInicial() + "–" + f.anoFinal(), anos);
        }
        var niveis = new java.util.TreeSet<>(r.niveis());
        if (!niveis.isEmpty()) {
            marca(marcas, partes, "nivel", String.join(" e ", niveis.stream().map(NIVEIS::get).toList()),
                niveis.stream().map(n -> "scholarity_ids%5B%5D=" + n).toList());
        }
        var dificuldades = new java.util.TreeSet<>(r.dificuldades());
        if (!dificuldades.isEmpty()) {
            var rotulo = dificuldades.size() == 1 ? DIFICULDADES.get(dificuldades.first())
                : dificuldades.last() - dificuldades.first() + 1 == dificuldades.size()
                    ? DIFICULDADES.get(dificuldades.first()) + " a " + DIFICULDADES.get(dificuldades.last()).toLowerCase(java.util.Locale.ROOT)
                    : String.join(", ", dificuldades.stream().map(DIFICULDADES::get).toList());
            marca(marcas, partes, "dificuldade", rotulo, dificuldades.stream().map(d -> "difficulty%5B%5D=" + d).toList());
        }
        if ("RECENTES".equals(r.ordem())) marca(marcas, partes, "ordem", "Mais recentes", List.of("sort=newest"));
        if ("DIFICEIS".equals(r.ordem())) marca(marcas, partes, "ordem", "Mais difíceis", List.of("sort=hardest"));
        if (r.soComentadas()) marca(marcas, partes, "comentadas", "Com gabarito comentado", List.of("has_professor_commentaries=true"));
        for (var a : r.aprendidos()) {
            var parametro = PARAMETRO_APRENDIDO.get(a.tipo());
            if (parametro == null || a.numero() <= 0) continue;
            marca(marcas, partes, a.tipo() + ":" + a.numero(), a.nome() == null ? a.tipo() + " nº " + a.numero() : a.nome(),
                List.of(parametro + "%5B%5D=" + a.numero()));
        }
        partes.sort(java.util.Comparator.comparing(p -> p.texto().substring(0, p.texto().indexOf('='))));
        return new Montado(BASE + "?" + String.join("&", partes.stream().map(Parte::texto).toList()), List.copyOf(marcas.values()));
    }

    private static void marca(Map<String, Marca> marcas, List<Parte> partes, String chave, String rotulo, List<String> parametros) {
        marcas.put(chave, new Marca(chave, rotulo, parametros));
        parametros.forEach(p -> partes.add(new Parte(chave, p)));
    }

    /**
     * D-176: os números de cargo, áreas e instituição num endereço do QConcursos, na ordem em que aparecem; o resto do endereço
     * é ignorado. Vazio se não houver nenhum.
     */
    public static List<Aprendido> aprendidos(String texto) {
        var achados = new ArrayList<Aprendido>();
        var query = query(texto);
        if (query == null) return achados;
        var porParametro = new java.util.HashMap<String, String>();
        PARAMETRO_APRENDIDO.forEach((tipo, parametro) -> porParametro.put(parametro, tipo));
        var vistos = new LinkedHashSet<String>();
        for (var par : query.split("&")) {
            var i = par.indexOf('=');
            if (i < 0) continue;
            var chave = URLDecoder.decode(par.substring(0, i), StandardCharsets.UTF_8).replace("[]", "");
            var tipo = porParametro.get(chave);
            var valor = URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8).trim();
            if (tipo == null || !NUMERO.matcher(valor).matches()) continue;
            var numero = Integer.parseInt(valor);
            if (numero > 0 && vistos.add(tipo + ":" + numero)) achados.add(new Aprendido(tipo, numero, null));
        }
        return achados;
    }

    private static String query(String texto) {
        if (texto == null) return null;
        try {
            return URI.create(texto.trim().replace("[", "%5B").replace("]", "%5D").replace(" ", "%20")).getRawQuery();
        } catch (IllegalArgumentException e) {
            return null;
        }
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
        var query = query(t);
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
