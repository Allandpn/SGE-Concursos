package br.com.estudos.conferencia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.conferencia.RegrasEdital.AssuntoRegra;
import br.com.estudos.conferencia.RegrasEdital.ParteRegra;

/**
 * As regras por texto contra os vetores que o planejamento gera (docs/SPRINT-25-CONFERENCIA-EDITAL.md §3, D-88, D-89): o
 * mesmo arquivo é testado lá (test_vetores_conferencia.py). Se a regra mudar de um lado, este teste ou aquele quebra.
 */
class RegrasEditalTest {

    private static final JsonNode VETORES = ler();

    private static JsonNode ler() {
        try (var in = RegrasEditalTest.class.getResourceAsStream("/conferencia/vetores.json")) {
            return JsonMapper.builder().build().readTree(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void normalizar_iguala_oPython() {
        for (var caso : VETORES.get("normalizar")) {
            assertEquals(caso.get("saida").asString(), RegrasEdital.normalizar(caso.get("entrada").asString()), caso.get("entrada").asString());
        }
    }

    @Test
    void palavras_igualaOPython() {
        for (var caso : VETORES.get("palavras")) {
            var esperado = new TreeSet<String>();
            caso.get("saida").forEach(w -> esperado.add(w.asString()));
            assertEquals(esperado, new TreeSet<>(RegrasEdital.palavras(caso.get("entrada").asString())), caso.get("entrada").asString());
        }
    }

    @Test
    void chavesDeParte_incluindoARepeticaoNoMesmoAssunto() {
        for (var seq : VETORES.get("chaves_de_parte")) {
            var vistas = RegrasEdital.contador();
            int i = 0;
            for (var p : seq.get("partes")) {
                var chave = RegrasEdital.chaveDaParte(seq.get("disciplina").asString(), seq.get("assunto").asString(),
                    nulo(p.get("herdado")), nulo(p.get("texto")), nulo(p.get("contexto")), vistas);
                assertEquals(seq.get("chaves").get(i++).asString(), chave);
            }
        }
    }

    @Test
    void chavesDeAssunto_incluindoARepeticao() {
        for (var seq : VETORES.get("chaves_de_assunto")) {
            var vistos = RegrasEdital.contador();
            int i = 0;
            for (var t : seq.get("textos")) {
                assertEquals(seq.get("chaves").get(i++).asString(), RegrasEdital.chaveDoAssunto(seq.get("disciplina").asString(), t.asString(), vistos));
            }
        }
    }

    @Test
    void conferirAssunto_dasMesmasMensagensQueOPython() {
        assertTrue(VETORES.get("conferencias").size() >= 10);
        for (var c : VETORES.get("conferencias")) {
            var partes = new ArrayList<ParteRegra>();
            for (var p : c.get("assunto").get("partes")) partes.add(new ParteRegra(nulo(p.get("texto")), nulo(p.get("contexto")), nulo(p.get("herdado"))));
            var referencias = new ArrayList<String>();
            c.get("referencias").forEach(r -> referencias.add(r.asString()));
            var alertas = new ArrayList<String>();
            var erros = RegrasEdital.conferirAssunto(new AssuntoRegra(c.get("assunto").get("texto").asString(), partes),
                c.get("texto_disciplina").asString(), c.get("cabecalho").asString(), referencias, alertas);
            assertEquals(lista(c.get("erros")), erros, "erros: " + c.get("nome").asString());
            assertEquals(lista(c.get("alertas")), alertas, "alertas: " + c.get("nome").asString());
        }
    }

    private static String nulo(JsonNode no) {
        return no == null || no.isNull() ? null : no.asString();
    }

    private static List<String> lista(JsonNode no) {
        var saida = new ArrayList<String>();
        no.forEach(x -> saida.add(x.asString()));
        return saida;
    }
}
