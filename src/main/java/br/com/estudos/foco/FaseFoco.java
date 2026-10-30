package br.com.estudos.foco;

import java.time.Instant;
import java.util.UUID;

/** Uma fase do Pomodoro como está gravada (01_DOMINIO §3.15). Nada aqui é derivado: o tempo corrido é {@link FocoCalculo}. */
public record FaseFoco(
    Long id,
    UUID tentativaId,
    Especie especie,
    Long assuntoId,
    Long segmentoId,
    Instant inicio,
    int duracaoProgramadaS,
    Instant pausadoDesde,
    int pausadoAcumuladoS,
    Instant fim,
    Motivo motivoFim,
    Instant avisoFimEm,
    Long sessaoId
) {
    public enum Especie { FOCO, PAUSA_CURTA, PAUSA_LONGA }

    /** ENCERRADA: a pessoa parou. VENCIDA: chegou à duração programada (o foco "completo" de D-105). */
    public enum Motivo { ENCERRADA, VENCIDA }

    public boolean emCurso() {
        return fim == null;
    }

    public boolean pausada() {
        return pausadoDesde != null;
    }
}
