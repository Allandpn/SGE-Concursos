package br.com.estudos.schema;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
        try (var ps = conexao.prepareStatement("""
                INSERT INTO disciplina (nome, peso)
                VALUES (?, ?)
                RETURNING id
                """)) {
            ps.setString(1, "Disciplina de teste");
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
}
