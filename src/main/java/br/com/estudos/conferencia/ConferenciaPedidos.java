package br.com.estudos.conferencia;

import java.util.List;

import br.com.estudos.shared.enums.TipoPeso;

/** Corpos das ações da conferência (docs/SPRINT-25-CONFERENCIA-EDITAL.md §5). */
public final class ConferenciaPedidos {

    private ConferenciaPedidos() {}

    /** Até 3 nós e a natureza do vínculo (EXATO, MAIS_AMPLO ou PARCIAL). */
    public record Nos(List<Long> nos, String natureza) {}

    public record TextoDaParte(String texto, String contexto, String herdado) {}

    public record Dividir(List<TextoDaParte> partes) {}

    public record Juntar(Long outraParteId, String texto, String contexto, String herdado) {}

    public record Motivo(String motivo) {}

    public record Comentario(String texto) {}

    public record Peso(TipoPeso peso) {}

    public record TextoDoAssunto(String texto) {}

    /** {@code acao}: DESCARTAR (com motivo) ou INCLUIR (como assunto, com o texto; trecho de linha fora pede a disciplina). */
    public record ResolverTrecho(String acao, String motivo, String texto, Long disciplinaId, String novaDisciplina) {}

    public record EditarDecisao(List<Long> nos, String natureza, String comentario) {}
}
