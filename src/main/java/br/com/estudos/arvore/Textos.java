package br.com.estudos.arvore;

import java.text.Normalizer;
import java.util.Locale;

/** Texto para comparar e buscar: sem acento, minúsculo, espaços limpos. */
final class Textos {

    private Textos() {}

    static String semAcento(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .trim();
    }
}
