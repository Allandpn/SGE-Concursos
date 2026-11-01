package br.com.estudos.acesso;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import br.com.estudos.acesso.FreioDeTentativas.Tipo;
import br.com.estudos.foco.RelogioMutavel;

/** D-112: limitado por origem e no total, bloqueio temporário e nunca permanente, código mais estrito. */
class FreioDeTentativasTest {

    final RelogioMutavel relogio = new RelogioMutavel(Instant.parse("2026-11-01T12:00:00Z"), ZoneId.of("UTC"));
    final FreioDeTentativas freio = new FreioDeTentativas(relogio);

    @Test
    void d112_cincoFalhasBloqueiamAOrigem_porQuinzeMinutos() {
        for (int i = 0; i < 4; i++) {
            freio.falhou(Tipo.PASSKEY, "a", true);
        }
        assertThat(freio.bloqueio(Tipo.PASSKEY, "a", true)).isEmpty();
        freio.falhou(Tipo.PASSKEY, "a", true);
        assertThat(freio.bloqueio(Tipo.PASSKEY, "a", true)).isPresent();
        assertThat(freio.bloqueio(Tipo.PASSKEY, "b", true)).isEmpty();
        relogio.avancar(Duration.ofMinutes(15).plusSeconds(1));
        assertThat(freio.bloqueio(Tipo.PASSKEY, "a", true)).isEmpty();
    }

    @Test
    void d112_falhasForaDaJanelaNaoSomam() {
        for (int i = 0; i < 4; i++) {
            freio.falhou(Tipo.PASSKEY, "a", true);
        }
        relogio.avancar(Duration.ofMinutes(11));
        freio.falhou(Tipo.PASSKEY, "a", true);
        assertThat(freio.bloqueio(Tipo.PASSKEY, "a", true)).isEmpty();
    }

    @Test
    void d112_codigoEMaisEstrito() {
        for (int i = 0; i < 3; i++) {
            freio.falhou(Tipo.CODIGO, "a", true);
        }
        var ate = freio.bloqueio(Tipo.CODIGO, "a", true);
        assertThat(ate).contains(relogio.instant().plus(Duration.ofHours(1)));
        assertThat(freio.bloqueio(Tipo.PASSKEY, "a", true)).isEmpty();
    }

    @Test
    void d112_bloqueioTotal_soParaPedidoPublico_eExpira() {
        for (int i = 0; i < 30; i++) {
            freio.falhou(Tipo.PASSKEY, "ip-" + i, true);
        }
        assertThat(freio.bloqueio(Tipo.PASSKEY, "ip-novo", true)).isPresent();
        assertThat(freio.bloqueio(Tipo.PASSKEY, "100.64.0.2", false)).isEmpty(); // o titular pela tailnet entra
        relogio.avancar(Duration.ofMinutes(16));
        assertThat(freio.bloqueio(Tipo.PASSKEY, "ip-novo", true)).isEmpty();
    }

    @Test
    void d112_acertoZeraAsFalhasDaOrigem() {
        for (int i = 0; i < 4; i++) {
            freio.falhou(Tipo.PASSKEY, "a", true);
        }
        freio.deuCerto(Tipo.PASSKEY, "a");
        freio.falhou(Tipo.PASSKEY, "a", true);
        assertThat(freio.bloqueio(Tipo.PASSKEY, "a", true)).isEmpty();
    }
}
