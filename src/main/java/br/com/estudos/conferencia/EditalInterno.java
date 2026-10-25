package br.com.estudos.conferencia;

import java.util.List;

import tools.jackson.databind.JsonNode;

/** Contratos das rotas internas do edital, só para o planejamento (docs/SPRINT-25-CONFERENCIA-EDITAL.md §4). */
public final class EditalInterno {

    private EditalInterno() {}

    /** Um edital com o cargo escolhido, esperando o planejamento. {@code slug} é a sugestão; o definitivo nasce na conclusão (D-64). */
    public record Pendente(Long id, String slug, String cargo, String nomeArquivo) {}

    /** A decisão da pessoa que vale antes da IA (contrato edital-revisao/1 §11.3). */
    public record Decisao(String parte, String contexto, List<Long> nos, String natureza, String escopo, String edital,
                          String arvore, String data, String origem) {}

    public record DadosEdital(Long id, String slug, String cargo, String concursoNome, String banca,
                              String pdf, String arvoreVigente, List<Decisao> decisoes) {}

    public record FalhaRequest(String etapa, String disciplina, String mensagem) {}

    public record ResumoEntrega(Long execucaoId, boolean repetida, int disciplinas, int assuntos, int partes,
                                int partesSemNo, int trechosAbertos) {}

    /** O corpo da entrega, já lido (o contrato é o JSON de edital-revisao/1). */
    public record Entrega(JsonNode json, String hash) {}
}
