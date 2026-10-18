package br.com.estudos.prova;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * D-72 provado sobre o mapeamento real, não sobre a intenção: nenhuma rota lista, busca ou
 * exporta questões fora do contexto de uma prova (docs/SPRINT-24-PROVA-GABARITO.md §4, §10).
 * É a estrutura que garante — o contrato simplesmente não tem a rota.
 */
class ProvaEstruturalTest extends ProvaTestBase {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamento;

    @Test
    void d72_nenhumaRotaDeQuestaoSemAProvaNoCaminho() {
        var encontradas = 0;
        for (var entrada : mapeamento.getHandlerMethods().entrySet()) {
            var condicao = entrada.getKey().getPathPatternsCondition();
            if (condicao == null) continue;
            for (var padrao : condicao.getPatternValues()) {
                if (padrao.toLowerCase().contains("questao") || padrao.toLowerCase().contains("questoes")) {
                    encontradas++;
                    assertTrue(padrao.matches(".*/provas/\\{[a-zA-Z]+}/.*"),
                        "rota de questão sem a prova no caminho: " + padrao + " (D-72)");
                }
            }
        }
        assertTrue(encontradas > 0, "esperava achar a rota de correção de atribuição, que tem questão no caminho");
    }

    @Test
    void d72_osControllersDaProvaSoVivemSobProvas() {
        for (var entrada : mapeamento.getHandlerMethods().entrySet()) {
            if (!entrada.getValue().getBeanType().getPackageName().equals("br.com.estudos.prova")) continue;
            var condicao = entrada.getKey().getPathPatternsCondition();
            for (var padrao : condicao.getPatternValues()) {
                assertTrue(padrao.startsWith("/api/provas") || padrao.startsWith("/internal/provas"),
                    "o pacote prova expõe uma rota fora de /provas: " + padrao);
            }
        }
    }

    @Test
    void d72_naoExisteListagemDeQuestoesNaRaiz() {
        for (var entrada : mapeamento.getHandlerMethods().entrySet()) {
            var condicao = entrada.getKey().getPathPatternsCondition();
            if (condicao == null) continue;
            for (var padrao : condicao.getPatternValues()) {
                assertFalse(padrao.equals("/api/questoes") || padrao.startsWith("/api/questoes/")
                        || padrao.equals("/internal/questoes") || padrao.startsWith("/internal/questoes/"),
                    "rota de questões na raiz: " + padrao);
            }
        }
    }
}
