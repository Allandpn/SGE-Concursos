package br.com.estudos.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Item 1.6 de PROGRESSO.md — verificação de ausência, caminho de banco
 * (docs/SPRINT-1-BANCO.md §5; 03_INVARIANTES §4.2: "Ausência não é garantia.
 * Ausência + teste estrutural é."). Lista branca fechada, não lista negra de
 * nomes proibidos — SPRINT-1-BANCO.md §2.2 já registra que um teste por nome
 * não distingue o `ativo` legítimo de um `status` ilegítimo; fechar o
 * conjunto obriga qualquer coluna ou tabela nova a ser decisão consciente de
 * quem editar este teste. O caminho de código (nenhuma entidade mapeia fase,
 * nenhuma classe representa turno) fica para a sprint que criar as entidades.
 */
class EstruturaSchemaTest extends RestricaoTestBase {

    @Test
    void d16_assuntoSemColunaDeFase() throws SQLException {
        Set<String> esperado = Set.of(
                "id", "disciplina_id", "nome", "peso", "dificuldade_percebida",
                "ordem", "ativo", "criado_em", "atualizado_em");
        assertEquals(esperado, colunasDeAssunto());
    }

    @Test
    void d28_semTabelaDeTurno() throws SQLException {
        Set<String> esperado = Set.of(
                "disciplina", "assunto", "sessao", "revisao", "erro", "parametro",
                "simulado", "resultado_simulado");
        assertEquals(esperado, tabelasDoSchema());
    }

    @Test
    void d13_resultadoSimuladoSemColunaDeAssunto() throws SQLException {
        Set<String> esperado = Set.of(
                "id", "simulado_id", "disciplina_id", "formato",
                "questoes_corretas", "questoes_total", "criado_em", "atualizado_em");
        assertEquals(esperado, colunasDe("resultado_simulado"));
    }

    private Set<String> colunasDeAssunto() throws SQLException {
        return colunasDe("assunto");
    }

    private Set<String> colunasDe(String tabela) throws SQLException {
        Set<String> colunas = new HashSet<>();
        try (PreparedStatement st = conexao.prepareStatement("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = 'public' AND table_name = ?
                        """)) {
            st.setString(1, tabela);
            try (var rs = st.executeQuery()) {
                while (rs.next()) {
                    colunas.add(rs.getString("column_name"));
                }
            }
        }
        return colunas;
    }

    private Set<String> tabelasDoSchema() throws SQLException {
        Set<String> tabelas = new HashSet<>();
        try (Statement st = conexao.createStatement();
                var rs = st.executeQuery("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                          AND table_name <> 'flyway_schema_history'
                        """)) {
            while (rs.next()) {
                tabelas.add(rs.getString("table_name"));
            }
        }
        return tabelas;
    }
}
