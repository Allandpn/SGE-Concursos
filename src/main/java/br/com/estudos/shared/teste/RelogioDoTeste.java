package br.com.estudos.shared.teste;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** O relógio do app (ADR-061): o do sistema e, nas requisições do modo de teste, mais o deslocamento em dias. */
public final class RelogioDoTeste extends Clock {

    private final Clock base;

    public RelogioDoTeste(Clock base) {
        this.base = base;
    }

    @Override
    public ZoneId getZone() {
        return base.getZone();
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return new RelogioDoTeste(base.withZone(zona));
    }

    @Override
    public Instant instant() {
        var agora = base.instant();
        return ModoTeste.ligado() ? agora.plus(Duration.ofDays(ModoTeste.deslocamentoDias())) : agora;
    }
}
