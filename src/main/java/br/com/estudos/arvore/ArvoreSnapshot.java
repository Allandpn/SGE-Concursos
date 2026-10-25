package br.com.estudos.arvore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Uma versão da árvore inteira em memória (cerca de 20 mil nós, poucos MB): a busca e o caminho de cada nó
 * dispensam o banco. Imutável depois de montada.
 */
final class ArvoreSnapshot {

    record No(long id, String nome, Long paiId, int ordem, long raizId, String normalizado) {}

    private final Map<Long, No> nos = new HashMap<>();
    private final Map<Long, List<No>> filhos = new HashMap<>();
    private final List<No> raizes = new ArrayList<>();
    private final String rotulo;

    ArvoreSnapshot(String rotulo, List<No> todos) {
        this.rotulo = rotulo;
        for (var no : todos) nos.put(no.id(), no);
        for (var no : todos) {
            if (no.paiId() == null) raizes.add(no);
            else filhos.computeIfAbsent(no.paiId(), k -> new ArrayList<>()).add(no);
        }
        var porOrdem = Comparator.comparingInt(No::ordem).thenComparingLong(No::id);
        raizes.sort(porOrdem);
        filhos.values().forEach(l -> l.sort(porOrdem));
    }

    String rotulo() {
        return rotulo;
    }

    Optional<No> no(long id) {
        return Optional.ofNullable(nos.get(id));
    }

    List<No> raizes() {
        return raizes;
    }

    List<No> filhosDe(long id) {
        return filhos.getOrDefault(id, List.of());
    }

    int quantidade() {
        return nos.size();
    }

    /** Da disciplina até o nó, com o nome de cada um. */
    List<String> caminho(No no) {
        var saida = new ArrayList<String>();
        var atual = no;
        while (atual != null) {
            saida.add(0, atual.nome());
            atual = atual.paiId() == null ? null : nos.get(atual.paiId());
        }
        return saida;
    }

    /**
     * Busca por texto sem acento: todas as palavras da consulta precisam estar no nome ou no caminho. Quem tem a
     * consulta inteira no nome vem primeiro; depois quem tem todas as palavras no nome; por fim as que só o caminho tem.
     */
    List<No> buscar(String consulta, int limite) {
        var q = Textos.semAcento(consulta);
        if (q.isEmpty()) return List.of();
        var palavras = q.split(" ");
        record Pontuado(No no, int nota) {}
        var achados = new ArrayList<Pontuado>();
        for (var no : nos.values()) {
            if (no.paiId() == null) continue; // a disciplina não é alvo de ligação (D-87 do planejamento: disciplina sozinha não casa)
            int nota;
            if (no.normalizado().contains(q)) {
                nota = no.normalizado().startsWith(q) ? 0 : 1;
            } else if (todas(no.normalizado(), palavras)) {
                nota = 2;
            } else if (todas(Textos.semAcento(String.join(" ", caminho(no))), palavras)) {
                nota = 3;
            } else {
                continue;
            }
            achados.add(new Pontuado(no, nota));
        }
        achados.sort(Comparator.comparingInt(Pontuado::nota)
            .thenComparingInt(p -> p.no().nome().length())
            .thenComparingLong(p -> p.no().id()));
        return achados.stream().limit(limite).map(Pontuado::no).toList();
    }

    private static boolean todas(String texto, String[] palavras) {
        for (var p : palavras) if (!texto.contains(p)) return false;
        return true;
    }
}
