package br.com.estudos.edital;

/**
 * A instrução da leitura do cabeçalho do edital (docs/SPRINT-18-CONCURSOS.md §4.4; a etapa 2 passou ao
 * planejamento, ADR-047). A IA só extrai.
 */
final class PromptsEdital {

    private PromptsEdital() {}

    static final String SISTEMA = """
        Você analisa editais de concursos públicos brasileiros para um sistema de estudos.
        Responda sempre com um único objeto JSON válido, sem texto antes ou depois.
        Seja fiel ao edital: não invente conteúdo, número ou data que o texto não traz.""";

    static String cargos(String texto) {
        return """
            ETAPA 1 — identificar o concurso e os cargos.

            Do edital abaixo, extraia:
            - "concurso": nome curto do concurso (órgão e ano, ex.: "SEFAZ SC 2026");
            - "banca": a banca organizadora, ou null;
            - "dataProva": data da prova objetiva no formato AAAA-MM-DD se o edital a fixa, senão null;
            - "cargos": a lista de cargos, áreas ou especialidades que têm conteúdo programático
              próprio, com o nome exatamente como aparece no edital.

            Formato: {"concurso": "...", "banca": "...", "dataProva": "AAAA-MM-DD", "cargos": ["..."]}

            EDITAL:
            <<<
            %s
            >>>""".formatted(texto);
    }
}
