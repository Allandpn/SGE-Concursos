package br.com.estudos.pergunta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Confere a resposta da IA antes de gravar (D-123; docs/SPRINT-32-PERGUNTAS.md §4): pergunta fora da forma é descartada,
 * as outras ficam. Nenhuma válida é falha da resposta.
 */
final class RespostaPerguntas {

    static final Set<String> TIPOS = Set.of("EVOCACAO", "COMPLETAR", "PORQUE", "COMPARACAO");

    record Ponto(String texto, boolean principal, int pagina) {}

    record Pergunta(String tipo, String enunciado, boolean marcada, List<Ponto> pontos) {}

    static class Invalida extends RuntimeException {
        Invalida(String mensagem) {
            super(mensagem);
        }
    }

    private RespostaPerguntas() {}

    static List<Pergunta> ler(String resposta, int paginas, JsonMapper json) {
        if (resposta == null) {
            throw new Invalida("A IA não respondeu.");
        }
        var inicio = resposta.indexOf('{');
        var fim = resposta.lastIndexOf('}');
        if (inicio < 0 || fim <= inicio) {
            throw new Invalida("A resposta da IA não tem JSON.");
        }
        JsonNode raiz;
        try {
            raiz = json.readTree(resposta.substring(inicio, fim + 1));
        } catch (RuntimeException e) {
            throw new Invalida("A resposta da IA não é JSON válido.");
        }
        var validas = new ArrayList<Pergunta>();
        var lista = raiz.get("perguntas");
        if (lista != null && lista.isArray()) {
            for (var p : lista) {
                var pergunta = pergunta(p, paginas);
                if (pergunta != null) {
                    validas.add(pergunta);
                }
            }
        }
        if (validas.isEmpty()) {
            throw new Invalida("Nenhuma pergunta da IA estava na forma combinada.");
        }
        return validas.subList(0, Math.min(validas.size(), 15));
    }

    private static Pergunta pergunta(JsonNode p, int paginas) {
        var tipo = texto(p, "tipo");
        var enunciado = texto(p, "enunciado");
        if (tipo == null || !TIPOS.contains(tipo.toUpperCase()) || enunciado == null || enunciado.length() > 600) {
            return null;
        }
        var pontos = new ArrayList<Ponto>();
        var lista = p.get("pontos");
        if (lista == null || !lista.isArray()) {
            return null;
        }
        for (var pt : lista) {
            var t = texto(pt, "texto");
            var pagina = pt.get("pagina");
            var principal = pt.get("principal");
            if (t == null || t.length() > 400 || pagina == null || !pagina.canConvertToInt() || principal == null || !principal.isBoolean()) {
                return null;
            }
            int n = pagina.asInt();
            if (n < 1 || n > paginas) {
                return null;
            }
            pontos.add(new Ponto(t, principal.asBoolean(), n));
        }
        if (pontos.isEmpty() || pontos.stream().noneMatch(Ponto::principal)) {
            return null;
        }
        var marcada = p.get("marcada");
        return new Pergunta(tipo.toUpperCase(), enunciado, marcada != null && marcada.asBoolean(false), pontos);
    }

    private static String texto(JsonNode no, String campo) {
        var v = no.get(campo);
        if (v == null || !v.isString()) {
            return null;
        }
        var s = v.asString().strip();
        return s.isEmpty() ? null : s;
    }
}
