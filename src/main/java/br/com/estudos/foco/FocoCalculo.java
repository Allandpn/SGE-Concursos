package br.com.estudos.foco;

import java.time.Duration;
import java.time.Instant;

import br.com.estudos.foco.FaseFoco.Especie;

/**
 * O tempo de uma fase, sempre calculado do início gravado (ADR-050, D-16): não existe coluna de tempo corrido.
 * Funções puras, testadas com instantes fixos.
 */
public final class FocoCalculo {

    private FocoCalculo() {}

    /** Segundos em que a fase esteve correndo: do início ao fim (ou a {@code agora}), menos as pausas manuais. */
    public static long decorridoS(FaseFoco fase, Instant agora) {
        var ate = fase.fim() != null ? fase.fim() : agora;
        long total = Duration.between(fase.inicio(), ate).toSeconds();
        long pausas = fase.pausadoAcumuladoS();
        if (fase.fim() == null && fase.pausadoDesde() != null) {
            pausas += Math.max(0, Duration.between(fase.pausadoDesde(), agora).toSeconds());
        }
        return Math.max(0, total - pausas);
    }

    /**
     * D-99 — o que conta como tempo de estudo: só a fase de foco, pelo tempo que de fato correu, nunca além da duração
     * programada (foco que venceu sem a pessoa encerrar vale a duração, não o tempo depois do vencimento).
     */
    public static long focoCorridoS(FaseFoco fase, Instant agora) {
        if (fase.especie() != Especie.FOCO) return 0;
        return Math.min(decorridoS(fase, agora), fase.duracaoProgramadaS());
    }

    public static long restanteS(FaseFoco fase, Instant agora) {
        return Math.max(0, fase.duracaoProgramadaS() - decorridoS(fase, agora));
    }

    /** Em curso, correndo (não pausada) e com a duração cumprida. Pausada nunca vence. */
    public static boolean venceu(FaseFoco fase, Instant agora) {
        return fase.emCurso() && !fase.pausada() && decorridoS(fase, agora) >= fase.duracaoProgramadaS();
    }

    /** O instante em que a fase em curso (não pausada) chega à duração programada. */
    public static Instant vencimento(FaseFoco fase) {
        return fase.inicio().plusSeconds((long) fase.duracaoProgramadaS() + fase.pausadoAcumuladoS());
    }

    /** Minutos para pré-preencher o tempo da sessão (D-100): arredonda; qualquer foco real vale ao menos 1. */
    public static int minutos(long segundos) {
        if (segundos <= 0) return 0;
        return (int) Math.max(1, Math.round(segundos / 60.0));
    }
}
