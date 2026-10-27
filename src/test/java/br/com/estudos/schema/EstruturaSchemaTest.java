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
                "ordem", "ativo", "criado_em", "atualizado_em", "chave_externa");
        assertEquals(esperado, colunasDeAssunto());
    }

    @Test
    void d28_semTabelaDeTurno() throws SQLException {
        Set<String> esperado = Set.of(
                "disciplina", "assunto", "sessao", "revisao", "erro", "parametro",
                "simulado", "resultado_simulado", "segmento",
                // Sprint 18 (ADR-042/043): concurso-alvo, cobertura do edital e
                // análise de edital. Nenhuma guarda fase, turno ou plano.
                "concurso", "concurso_assunto", "analise_edital",
                // Sprint 19 (ADR-044): configuração de IA e a lacuna de
                // material (D-65), um fato registrado — nenhuma guarda estado
                // do material, que é derivado dos segmentos.
                "configuracao_ia", "lacuna_material",
                // Sprint 24 (ADR-046): a prova hospedada, a verificação do material e a
                // resolução. O veredito é um fato registrado (D-73); a nota não é guardada
                // aqui (D-16) — M-1 continua entrando por resultado_simulado. Nenhuma
                // guarda fase, turno ou plano.
                "prova", "questao", "alternativa", "veredito_questao", "tentativa_prova", "resposta_prova",
                // Sprint 25 (ADR-047): a árvore do QConcursos e a leitura do edital entregue pelo planejamento,
                // mais as decisões e o histórico da pessoa. A fila de revisão e as contagens são consultas
                // (D-16); nada aqui guarda fase, turno ou plano.
                "arvore_versao", "no_arvore", "no_arvore_diferenca", "edital_execucao", "edital_disciplina",
                "edital_assunto", "edital_parte", "edital_parte_no", "edital_trecho", "edital_decisao", "edital_evento",
                // Redesenho do fluxo (passo B): o destino que a pessoa deu a uma lacuna do material.
                "edital_lacuna_decisao");
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
