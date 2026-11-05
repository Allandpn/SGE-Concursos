package br.com.estudos.shared.teste;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

/**
 * O modo de teste (ADR-061; docs/SPRINT-41-MODO-DE-TESTE.md §2): o schema {@code teste}, a cópia dos dados reais e a data do teste.
 * Sempre pelo pool real e com nomes qualificados, para nunca depender do roteamento da requisição.
 */
@Service
public class ModoTesteService implements ApplicationRunner {

    public record Estado(boolean preparado, int deslocamentoDias, Instant copiadoEm) {}

    public record Copia(int tabelas, Map<String, Integer> linhas) {}

    /** O que nunca vai para o teste: o acesso, os avisos, a IA e o histórico das migrações. */
    static final Set<String> FORA_DA_COPIA = Set.of("flyway_schema_history", "passkey", "sessao_acesso", "codigo_recuperacao",
        "push_config", "push_inscricao", "configuracao_ia", "captura_chave", "questao_capturada", "captura_diagnostico");

    private static final String S = ModoTesteConfig.SCHEMA;

    private final HikariDataSource real;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final Clock clock;
    private volatile Boolean preparadoEmCache;

    public ModoTesteService(@Qualifier("dataSourceReal") HikariDataSource real, Clock clock) {
        this.real = real;
        this.jdbc = new JdbcTemplate(real);
        this.transacao = new TransactionTemplate(new DataSourceTransactionManager(real));
        this.clock = clock;
    }

    /** Na subida: se o schema de teste já existe, ele acompanha as migrações novas. */
    @Override
    public void run(ApplicationArguments args) {
        if (schemaExiste()) migrar();
    }

    /** Para os testes, depois de apagar o schema. */
    void esquecerCache() {
        preparadoEmCache = null;
    }

    public boolean preparado() {
        var p = preparadoEmCache;
        if (p == null) {
            p = schemaExiste() && jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'modo_teste_estado'", Integer.class, S) > 0;
            preparadoEmCache = p;
        }
        return p;
    }

    public Estado estado() {
        if (!preparado()) return new Estado(false, 0, null);
        return jdbc.queryForObject("SELECT deslocamento_dias, copiado_em FROM " + S + ".modo_teste_estado WHERE id = 1",
            (rs, n) -> new Estado(true, rs.getInt(1), rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()));
    }

    /** O deslocamento do teste, para o filtro ligar o relógio da requisição. */
    public int deslocamento() {
        return jdbc.queryForObject("SELECT deslocamento_dias FROM " + S + ".modo_teste_estado WHERE id = 1", Integer.class);
    }

    /** Entrar: cria e migra o schema e, se nunca foi copiado, copia os dados reais. */
    public synchronized Estado entrar() {
        if (!schemaExiste()) jdbc.execute("CREATE SCHEMA " + S);
        migrar();
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + S + ".modo_teste_estado (id INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1), "
            + "deslocamento_dias INTEGER NOT NULL DEFAULT 0, copiado_em TIMESTAMPTZ)");
        jdbc.update("INSERT INTO " + S + ".modo_teste_estado (id) VALUES (1) ON CONFLICT (id) DO NOTHING");
        preparadoEmCache = true;
        if (estado().copiadoEm() == null) copiar();
        return estado();
    }

    /** Recomeçar: a cópia de novo, e a data volta a hoje. */
    public synchronized Copia recomecar() {
        if (!preparado()) entrar();
        return copiar();
    }

    public Estado deslocar(int dias) {
        jdbc.update("UPDATE " + S + ".modo_teste_estado SET deslocamento_dias = ? WHERE id = 1", dias);
        return estado();
    }

    public Estado irPara(LocalDate data) {
        return deslocar((int) ChronoUnit.DAYS.between(LocalDate.now(Clock.system(clock.getZone())), data));
    }

    /**
     * Esvazia as tabelas do teste e copia as do real na ordem das chaves estrangeiras, com os mesmos ids; acerta as identidades. Tudo
     * numa transação: falhou, o teste fica como estava.
     */
    Copia copiar() {
        return transacao.execute(st -> {
            var tabelas = tabelasEmOrdem();
            if (!tabelas.isEmpty()) {
                jdbc.execute("TRUNCATE " + String.join(", ", tabelas.stream().map(t -> S + "." + t).toList()) + " CASCADE");
            }
            var linhas = new LinkedHashMap<String, Integer>();
            for (var t : tabelas) {
                var colunas = String.join(", ", colunasCopiaveis(t));
                linhas.put(t, jdbc.update("INSERT INTO " + S + "." + t + " (" + colunas + ") OVERRIDING SYSTEM VALUE SELECT " + colunas
                    + " FROM public." + t));
            }
            acertarIdentidades(tabelas);
            jdbc.update("UPDATE " + S + ".modo_teste_estado SET copiado_em = now(), deslocamento_dias = 0 WHERE id = 1");
            return new Copia(tabelas.size(), linhas);
        });
    }

    private boolean schemaExiste() {
        return jdbc.queryForObject("SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?", Integer.class, S) > 0;
    }

    private void migrar() {
        Flyway.configure().dataSource(real).schemas(S).defaultSchema(S).locations("classpath:db/migration").load().migrate();
    }

    /** As tabelas de estudo do real que existem no teste, pais antes dos filhos. */
    List<String> tabelasEmOrdem() {
        var doReal = new TreeSet<>(jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'", String.class));
        var doTeste = new HashSet<>(jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE'", String.class, S));
        doReal.removeIf(t -> FORA_DA_COPIA.contains(t) || !doTeste.contains(t));
        var pais = new HashMap<String, Set<String>>();
        jdbc.query("""
            SELECT DISTINCT c.conrelid::regclass::text, c.confrelid::regclass::text FROM pg_constraint c
            JOIN pg_namespace n ON n.oid = c.connamespace WHERE c.contype = 'f' AND n.nspname = 'public'""",
            rs -> { pais.computeIfAbsent(semSchema(rs.getString(1)), k -> new HashSet<>()).add(semSchema(rs.getString(2))); });
        var ordem = new ArrayList<String>();
        var visitado = new HashSet<String>();
        for (var t : doReal) visitar(t, doReal, pais, visitado, ordem);
        return ordem;
    }

    private static void visitar(String t, Set<String> alvo, Map<String, Set<String>> pais, Set<String> visitado, List<String> ordem) {
        if (!visitado.add(t)) return;
        for (var p : pais.getOrDefault(t, Set.of())) {
            if (!p.equals(t) && alvo.contains(p)) visitar(p, alvo, pais, visitado, ordem);
        }
        ordem.add(t);
    }

    private static String semSchema(String nome) {
        var i = nome.lastIndexOf('.');
        return (i < 0 ? nome : nome.substring(i + 1)).replace("\"", "");
    }

    private List<String> colunasCopiaveis(String tabela) {
        return jdbc.queryForList("""
            SELECT '"' || column_name || '"' FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = ? AND is_generated = 'NEVER' ORDER BY ordinal_position""", String.class, tabela);
    }

    private void acertarIdentidades(List<String> tabelas) {
        for (var t : tabelas) {
            jdbc.query("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND (is_identity = 'YES' OR column_default LIKE 'nextval(%')""",
                rs -> {
                    var col = rs.getString(1);
                    jdbc.execute("SELECT setval(pg_get_serial_sequence('" + S + "." + t + "', '" + col + "'), "
                        + "coalesce((SELECT max(\"" + col + "\") FROM " + S + "." + t + "), 0) + 1, false)");
                }, S, t);
        }
    }
}
