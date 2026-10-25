package br.com.estudos.conferencia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * As regras por texto que o planejamento calcula e que o SGE refaz quando a pessoa edita, divide ou junta uma parte
 * (01_DOMINIO §3.12, D-88, D-89; docs/SPRINT-25-CONFERENCIA-EDITAL.md §3). É um porte de
 * {@code integracao/partes_edital.py} (normalizar, palavras, conferir_assunto) e {@code integracao/entrega_revisao.py}
 * (as chaves); a fonte da verdade é o Python, e os dois lados testam com o mesmo arquivo de vetores
 * ({@code integracao/casos-de-teste/vetores-conferencia.json}).
 */
public final class RegrasEdital {

    private RegrasEdital() {}

    /** Palavras sem peso para a conferência (as mesmas de {@code partes_edital.VAZIAS}). */
    private static final Set<String> VAZIAS = Set.of("de", "da", "do", "das", "dos", "e", "em", "a", "o", "as", "os", "para",
        "com", "por", "no", "na", "nos", "nas", "ao", "aos", "um", "uma", "sua", "seu", "suas", "seus", "ou", "que", "se",
        "sobre", "entre", "como", "pela", "pelo", "pelas", "pelos");

    public record ParteRegra(String texto, String contexto, String herdado) {}

    public record AssuntoRegra(String texto, List<ParteRegra> partes) {}

    /** Sem acento, minúsculas, sem hífen de quebra de linha, só letras/números e espaço. */
    public static String normalizar(String texto) {
        var decomposto = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFKD);
        var ascii = new StringBuilder();
        for (int i = 0; i < decomposto.length(); i++) {
            char c = decomposto.charAt(i);
            if (c < 128) ascii.append(c);
        }
        var t = ascii.toString().toLowerCase(Locale.ROOT).replaceAll("(\\w)-\\s+(\\w)", "$1$2");
        var palavras = new ArrayList<String>();
        var m = java.util.regex.Pattern.compile("[a-z0-9]+").matcher(t);
        while (m.find()) palavras.add(m.group());
        return String.join(" ", palavras);
    }

    /** As palavras de um texto, sem as vazias e com o plural simples tirado. */
    public static Set<String> palavras(String texto) {
        var saida = new HashSet<String>();
        var n = normalizar(texto);
        if (n.isEmpty()) return saida;
        for (var w : n.split(" ")) {
            if (VAZIAS.contains(w)) continue;
            saida.add(w.length() > 4 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w);
        }
        return saida;
    }

    /**
     * Chave estável da parte: disciplina, assunto, prefixo herdado, texto e contexto normalizados, mais o número da ocorrência
     * quando o edital repete a mesma parte no mesmo assunto. {@code vistas} é do assunto e vai sendo preenchido.
     */
    public static String chaveDaParte(String disciplina, String assunto, String herdado, String texto, String contexto,
                                      Map<String, Integer> vistas) {
        var base = String.join("|", normalizar(disciplina), normalizar(assunto), normalizar(herdado), normalizar(texto), normalizar(contexto));
        int n = vistas.merge(base, 1, Integer::sum);
        return sha1(base + "|" + n);
    }

    /** Chave estável do assunto: disciplina e texto normalizados, mais a ocorrência se o edital o repete. */
    public static String chaveDoAssunto(String disciplina, String texto, Map<String, Integer> vistos) {
        var base = String.join("|", normalizar(disciplina), normalizar(texto));
        int n = vistos.merge(base, 1, Integer::sum);
        return sha1("assunto|" + base + "|" + n);
    }

    /**
     * Erros de um assunto e das partes dele (lista vazia = aceito), como {@code partes_edital.conferir_assunto}. O que
     * for aviso e não erro (contexto que vem de um assunto anterior) vai para {@code alertas}.
     */
    public static List<String> conferirAssunto(AssuntoRegra assunto, String textoDisciplina, String cabecalho,
                                               List<String> referencias, List<String> alertas) {
        var erros = new ArrayList<String>();
        var textoNormalizado = normalizar(assunto.texto());
        var doCabecalho = palavras(cabecalho);
        var doAssunto = palavras(assunto.texto());
        var semCabecalho = new HashSet<>(doAssunto);
        semCabecalho.removeAll(doCabecalho);
        for (var r : referencias) {
            var nr = normalizar(r);
            if (!nr.isEmpty() && textoNormalizado.contains(nr) && palavras(r).size() >= 0.8 * semCabecalho.size()) return erros;
        }
        if (!normalizar(textoDisciplina).contains(textoNormalizado)) {
            erros.add("o assunto não é trecho do edital (foi reescrito ou juntou pedaços)");
        }
        if (assunto.partes() == null || assunto.partes().isEmpty()) {
            erros.add("assunto sem partes");
            return erros;
        }
        var cobertas = new HashSet<String>();
        var daDisciplina = palavras(textoDisciplina);
        for (var p : assunto.partes()) {
            var herdado = palavras(p.herdado());
            var contexto = palavras(p.contexto());
            var doTexto = palavras(p.texto());
            var alheias = diferenca(doTexto, doAssunto);
            if (!alheias.isEmpty()) {
                erros.add("parte " + repr(p.texto()) + " usa palavras que não estão no assunto: " + String.join(", ", alheias));
            }
            if (!diferenca(herdado, doAssunto).isEmpty()) {
                erros.add("prefixo herdado " + repr(p.herdado()) + " não está no assunto");
            }
            if (!diferenca(contexto, doAssunto).isEmpty()) {
                if (daDisciplina.containsAll(contexto)) {
                    var aviso = "contexto " + repr(p.contexto()) + " vem de fora do assunto (de um assunto anterior)";
                    if (!alertas.contains(aviso)) alertas.add(aviso);
                } else {
                    erros.add("contexto " + repr(p.contexto()) + " não está no assunto nem no texto da disciplina");
                }
            }
            cobertas.addAll(doTexto);
            cobertas.addAll(contexto);
            cobertas.addAll(herdado);
        }
        var faltam = new TreeSet<String>();
        for (var w : doAssunto) {
            if (cobertas.contains(w) || doCabecalho.contains(w)) continue;
            if (w.chars().allMatch(Character::isDigit) && w.length() <= 2) continue; // 4.1, 5.3
            faltam.add(w);
        }
        if (!faltam.isEmpty()) erros.add("palavras do assunto sem parte: " + String.join(", ", faltam));
        return erros;
    }

    private static TreeSet<String> diferenca(Set<String> a, Set<String> b) {
        var saida = new TreeSet<String>(a);
        saida.removeAll(b);
        return saida;
    }

    /** Como o {@code repr} de uma string do Python: aspas simples, ou duplas se o texto tem aspa simples e nenhuma dupla. */
    static String repr(String s) {
        if (s == null) return "None";
        char aspa = s.contains("'") && !s.contains("\"") ? '"' : '\'';
        var sb = new StringBuilder().append(aspa);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c == aspa) sb.append('\\');
                    sb.append(c);
                }
            }
        }
        return sb.append(aspa).toString();
    }

    private static String sha1(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(texto.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Uma chave nova de ocorrência por assunto. */
    public static Map<String, Integer> contador() {
        return new HashMap<>();
    }
}
