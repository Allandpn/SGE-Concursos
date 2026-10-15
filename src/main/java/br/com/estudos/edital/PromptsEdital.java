package br.com.estudos.edital;

import java.util.List;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.disciplina.Disciplina;

/**
 * As duas instruções da análise (docs/SPRINT-18-CONCURSOS.md §4.4 e §4.5).
 * A IA só extrai; peso e validação das correspondências são do servidor
 * (D-61, D-63).
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

    static String assuntos(String texto, String cargo, List<Disciplina> disciplinas, List<Assunto> assuntos) {
        var base = new StringBuilder();
        base.append("Disciplinas já cadastradas (id | nome):\n");
        disciplinas.forEach(d -> base.append(d.getId()).append(" | ").append(d.getNome()).append('\n'));
        base.append("\nAssuntos já cadastrados (id | nome | disciplina):\n");
        assuntos.forEach(a -> base.append(a.getId()).append(" | ").append(a.getNome())
            .append(" | ").append(a.getDisciplina().getNome()).append('\n'));

        return """
            ETAPA 2 — separar e classificar os assuntos do cargo escolhido.

            Cargo escolhido: %s

            Do conteúdo programático do edital abaixo, liste as disciplinas e os assuntos que
            valem para esse cargo — inclusive conhecimentos gerais ou básicos que o edital aplica
            a ele. Regras:
            - um assunto por tópico do conteúdo programático, na ordem do edital; separe itens
              agrupados num tópico só quando forem conteúdos distintos;
            - nome do assunto curto e fiel ao edital (no máximo 250 caracteres);
            - "questoes": número de questões daquela disciplina para esse cargo no quadro de
              provas do edital; null se o edital não informa. Nunca estime;
            - "disciplinaExistenteId": o id de uma disciplina já cadastrada que é a mesma
              disciplina, ou null;
            - "assuntoExistenteId": o id de um assunto já cadastrado só quando for claramente o
              mesmo conteúdo, ou null. Na dúvida, null.

            Formato: {"disciplinas": [{"nome": "...", "questoes": 10, "disciplinaExistenteId": null,
              "assuntos": [{"nome": "...", "assuntoExistenteId": null}]}]}

            %s
            EDITAL:
            <<<
            %s
            >>>""".formatted(cargo, base, texto);
    }
}
