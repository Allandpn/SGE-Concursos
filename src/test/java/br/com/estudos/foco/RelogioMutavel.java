package br.com.estudos.foco;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Relógio de teste que só anda quando o teste manda: os prazos do Pomodoro são provados com instantes controlados. */
public class RelogioMutavel extends Clock {

    private volatile Instant agora;
    private final ZoneId zona;

    public RelogioMutavel(Instant inicio, ZoneId zona) {
        this.agora = inicio;
        this.zona = zona;
    }

    public void definir(Instant instante) {
        this.agora = instante;
    }

    public void avancar(Duration duracao) {
        this.agora = agora.plus(duracao);
    }

    @Override
    public ZoneId getZone() {
        return zona;
    }

    @Override
    public Clock withZone(ZoneId novaZona) {
        return new RelogioMutavel(agora, novaZona);
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
