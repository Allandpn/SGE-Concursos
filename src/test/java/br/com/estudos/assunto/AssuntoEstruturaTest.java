package br.com.estudos.assunto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * D-16 (01_DOMINIO §10), caminho de código — item 2.9 de PROGRESSO.md.
 * Complementa d16_assuntoSemColunaDeFase (schema.EstruturaSchemaTest,
 * Sprint 1), que só prova ausência no banco. Lista branca fechada dos
 * atributos mapeados de Assunto: campo novo, inclusive @Transient, exige
 * edição consciente deste teste (mesmo espírito de EstruturaSchemaTest).
 * Reflexão pura, sem banco — não é @Tag("integracao").
 */
class AssuntoEstruturaTest {

    @Test
    void d16_nenhumAtributoMapeadoForaDaListaFechada() {
        var esperado = Set.of(
            "id", "disciplina", "nome", "peso", "dificuldadePercebida",
            "ordem", "ativo", "criadoEm", "atualizadoEm", "chaveExterna");

        var encontrado = Arrays.stream(Assunto.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertEquals(esperado, encontrado);
    }
}
