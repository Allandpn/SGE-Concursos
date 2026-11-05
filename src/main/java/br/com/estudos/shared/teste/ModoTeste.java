package br.com.estudos.shared.teste;

/**
 * O estado da requisição no modo de teste (docs/04_FRONTEND.md §8C; ADR-061; docs/SPRINT-41-MODO-DE-TESTE.md §2): ligado pelo
 * {@link ModoTesteFiltro}, lido pelo roteamento do banco e pelo relógio. Fora de uma requisição marcada (tarefas agendadas, o filtro
 * de acesso), sempre desligado.
 */
public final class ModoTeste {

    private record Estado(boolean ligado, int deslocamentoDias) {}

    private static final ThreadLocal<Estado> ESTADO = new ThreadLocal<>();

    private ModoTeste() {}

    public static boolean ligado() {
        var e = ESTADO.get();
        return e != null && e.ligado();
    }

    public static int deslocamentoDias() {
        var e = ESTADO.get();
        return e == null ? 0 : e.deslocamentoDias();
    }

    static void ligar(int deslocamentoDias) {
        ESTADO.set(new Estado(true, deslocamentoDias));
    }

    static void desligar() {
        ESTADO.remove();
    }
}
