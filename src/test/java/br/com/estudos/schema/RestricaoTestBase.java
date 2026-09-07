package br.com.estudos.schema;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base dos testes de restrição do schema (itens 1.5 e 1.6 de PROGRESSO.md).
 * Sobe um Postgres real uma única vez por suíte e aplica V1/V2/V3 como
 * produção aplicaria (ADR-017) — nunca H2. Ver docs/SPRINT-1-TESTES.md.
 */
@Tag("integracao")
@Testcontainers
abstract class RestricaoTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    protected Connection conexao;

    @BeforeAll
    static void subirEMigrar() {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
    }

    @BeforeEach
    void abrirTransacao() throws SQLException {
        conexao = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        conexao.setAutoCommit(false);
    }

    @AfterEach
    void desfazerTransacao() throws SQLException {
        conexao.rollback();
        conexao.close();
    }

    /** Disciplina mínima válida — setup para testes que precisam só de um id. */
    protected long inserirDisciplina() throws SQLException {
        return inserirDisciplina("");
    }

    /**
     * Sufixo: para testes que precisam de DUAS disciplinas na mesma
     * transação (ex. D-51/D-52, que exigem dois assuntos "independentes")
     * — sem ele, a segunda chamada colide em D-47 (nome de disciplina único),
     * já que o nome base é sempre o mesmo.
     */
    protected long inserirDisciplina(String sufixo) throws SQLException {
        try (var ps = conexao.prepareStatement("""
                INSERT INTO disciplina (nome, peso)
                VALUES (?, ?)
                RETURNING id
                """)) {
            ps.setString(1, "Disciplina de teste" + sufixo);
            ps.setString(2, "MEDIO");
            var rs = ps.executeQuery();
            rs.next();
            return rs.getLong("id");
        }
    }

    /** Assunto mínimo válido (D-41 já exige ordem) — setup, não é o que o teste examina. */
    protected long inserirAssunto(long disciplinaId) throws SQLException {
        try (var ps = conexao.prepareStatement("""
                INSERT INTO assunto (disciplina_id, nome, peso, dificuldade_percebida, ordem)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """)) {
            ps.setLong(1, disciplinaId);
            ps.setString(2, "Assunto de teste");
            ps.setString(3, "MEDIO");
            ps.setInt(4, 3);
            ps.setInt(5, 1);
            var rs = ps.executeQuery();
            rs.next();
            return rs.getLong("id");
        }
    }

    /** Segmento mínimo válido, chave externa aleatória — setup para testes de D-50/D-51/D-53. */
    protected long inserirSegmento(long assuntoId) throws SQLException {
        try (var ps = conexao.prepareStatement("""
                INSERT INTO segmento (assunto_id, chave_externa, ordem, arquivo)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """)) {
            ps.setLong(1, assuntoId);
            ps.setString(2, UUID.randomUUID().toString());
            ps.setInt(3, 1);
            ps.setString(4, "https://drive.example/segmento-de-teste");
            var rs = ps.executeQuery();
            rs.next();
            return rs.getLong("id");
        }
    }
}
