package br.com.estudos.pergunta;

import java.util.List;

/** O pedido à IA (docs/SPRINT-32-PERGUNTAS.md §4; 01_DOMINIO §3.18, D-123). */
final class PromptsPerguntas {

    private PromptsPerguntas() {}

    static final String SISTEMA = """
        Você prepara perguntas de recuperação ativa para quem estuda para concurso público. A pessoa vai responder DE MEMÓRIA,
        sem o material aberto, e só depois conferir o gabarito. Suas perguntas são pistas de evocação, nunca resumo.

        Prepare de 8 a 12 perguntas sobre o conteúdo do bloco, usando só o que está no texto. Tipos:
        - EVOCACAO: pede para listar ou reconstruir ("Quais são…", "Como funciona…").
        - COMPLETAR: uma frase do conteúdo com lacunas escritas como ____ (uma a três lacunas).
        - PORQUE: pede a razão de algo ("Por que…").
        - COMPARACAO: pede a diferença entre dois conceitos próximos ("Qual a diferença entre…").

        Regras:
        - Nunca coloque a resposta no enunciado. O enunciado é curto e não copia o parágrafo do material.
        - Cada pergunta tem um gabarito em pontos: o que a resposta precisa trazer. Cada ponto é curto (até 15 palavras).
        - Marque "principal": true nos pontos que formam a estrutura do assunto e false nos detalhes. Toda pergunta tem ao menos um principal.
        - "pagina" é o número que aparece em [página N] antes do trecho de onde o ponto saiu.
        - Os trechos marcados pela pessoa têm prioridade: DUVIDA puxa PORQUE e COMPARACAO; DECORAR puxa COMPLETAR; IMPORTANTE puxa EVOCACAO.
          Nessas perguntas, use "marcada": true.
        - Varie os tipos e cubra o bloco inteiro, não só o começo.

        Responda SOMENTE com JSON, sem texto antes ou depois, neste formato:
        {"perguntas":[{"tipo":"EVOCACAO","enunciado":"…","marcada":false,
          "pontos":[{"texto":"…","principal":true,"pagina":3}]}]}
        """;

    record Marcacao(String cor, String citacao, int pagina, String nota) {}

    static String usuario(String assunto, TextoDoBloco.Texto texto, List<Marcacao> marcacoes) {
        var corpo = new StringBuilder();
        corpo.append("Assunto: ").append(assunto).append("\n\n");
        if (!marcacoes.isEmpty()) {
            corpo.append("Trechos marcados pela pessoa:\n");
            for (var m : marcacoes) {
                corpo.append("- [").append(m.cor()).append("] \"").append(m.citacao()).append("\" (página ").append(m.pagina()).append(")");
                if (m.nota() != null && !m.nota().isBlank()) {
                    corpo.append(" — nota: ").append(m.nota().strip());
                }
                corpo.append('\n');
            }
            corpo.append('\n');
        }
        corpo.append("Texto do bloco (").append(texto.paginas()).append(" páginas):\n\n").append(texto.conteudo());
        return corpo.toString();
    }

}
