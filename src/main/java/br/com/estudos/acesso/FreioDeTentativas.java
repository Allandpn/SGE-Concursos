package br.com.estudos.acesso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * D-112 (docs/SPRINT-30-LOGIN.md §4): falhas de login limitadas por origem e no total, com bloqueio temporário e nunca
 * permanente. Em memória: há um contêiner só, e reiniciar o app zera tudo. O bloqueio total só vale para pedidos da
 * internet pública — o titular, pela tailnet, sempre tem um caminho, e um atacante não o tranca do lado de fora.
 */
@Component
public class FreioDeTentativas {

    /** Os dois caminhos de entrar, com limites diferentes (o código é mais estrito). */
    public enum Tipo {
        PASSKEY(5, Duration.ofMinutes(10), Duration.ofMinutes(15), 30, Duration.ofMinutes(10), Duration.ofMinutes(15)),
        CODIGO(3, Duration.ofMinutes(30), Duration.ofHours(1), 10, Duration.ofHours(24), Duration.ofHours(1));

        final int porOrigem;
        final Duration janelaOrigem;
        final Duration bloqueioOrigem;
        final int total;
        final Duration janelaTotal;
        final Duration bloqueioTotal;

        Tipo(int porOrigem, Duration janelaOrigem, Duration bloqueioOrigem, int total, Duration janelaTotal, Duration bloqueioTotal) {
            this.porOrigem = porOrigem;
            this.janelaOrigem = janelaOrigem;
            this.bloqueioOrigem = bloqueioOrigem;
            this.total = total;
            this.janelaTotal = janelaTotal;
            this.bloqueioTotal = bloqueioTotal;
        }
    }

    private record Chave(Tipo tipo, String origem) {}

    private final Clock relogio;
    private final Map<Chave, Deque<Instant>> falhasPorOrigem = new HashMap<>();
    private final Map<Chave, Instant> bloqueadoAte = new HashMap<>();
    private final Map<Tipo, Deque<Instant>> falhasTotais = new HashMap<>();
    private final Map<Tipo, Instant> totalBloqueadoAte = new HashMap<>();

    public FreioDeTentativas(Clock relogio) {
        this.relogio = relogio;
    }

    /** Até quando este pedido está bloqueado; vazio se pode tentar. */
    public synchronized Optional<Instant> bloqueio(Tipo tipo, String origem, boolean publico) {
        var agora = relogio.instant();
        var ate = bloqueadoAte.get(new Chave(tipo, origem));
        if (ate != null && ate.isAfter(agora)) {
            return Optional.of(ate);
        }
        var total = totalBloqueadoAte.get(tipo);
        if (publico && total != null && total.isAfter(agora)) {
            return Optional.of(total);
        }
        return Optional.empty();
    }

    public synchronized void falhou(Tipo tipo, String origem, boolean publico) {
        var agora = relogio.instant();
        var chave = new Chave(tipo, origem);
        var daOrigem = falhasPorOrigem.computeIfAbsent(chave, c -> new ArrayDeque<>());
        if (registrar(daOrigem, agora, tipo.janelaOrigem) >= tipo.porOrigem) {
            bloqueadoAte.put(chave, agora.plus(tipo.bloqueioOrigem));
            daOrigem.clear();
        }
        if (publico) {
            var todas = falhasTotais.computeIfAbsent(tipo, t -> new ArrayDeque<>());
            if (registrar(todas, agora, tipo.janelaTotal) >= tipo.total) {
                totalBloqueadoAte.put(tipo, agora.plus(tipo.bloqueioTotal));
                todas.clear();
            }
        }
    }

    /** Entrar com sucesso zera as falhas daquela origem. */
    public synchronized void deuCerto(Tipo tipo, String origem) {
        falhasPorOrigem.remove(new Chave(tipo, origem));
    }

    /** Só para os testes: o freio é estado de memória do contexto, compartilhado entre eles. */
    synchronized void limpar() {
        falhasPorOrigem.clear();
        bloqueadoAte.clear();
        falhasTotais.clear();
        totalBloqueadoAte.clear();
    }

    private static int registrar(Deque<Instant> falhas, Instant agora, Duration janela) {
        falhas.addLast(agora);
        var limite = agora.minus(janela);
        while (!falhas.isEmpty() && !falhas.peekFirst().isAfter(limite)) {
            falhas.removeFirst();
        }
        return falhas.size();
    }
}
