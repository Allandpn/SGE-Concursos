package br.com.estudos.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * Item 1.5 de PROGRESSO.md — um teste por restrição das 9 de
 * docs/SPRINT-1-BANCO.md §3 (D-18 fica fora: teste estrutural, item 1.6).
 * Padrão de asserção em docs/SPRINT-1-TESTES.md §6: confere o NOME da
 * restrição violada, não só que algo lançou.
 */
class RestricoesInvariantesTest extends RestricaoTestBase {

    // D-05 — molde. A crítica (03_INVARIANTES §4.1): duas revisões PENDENTE
    // do mesmo assunto seriam duas escadas paralelas, silenciosamente.
    @Test
    void d05_segundaRevisaoPendenteMesmoAssunto_recusada() throws SQLException {
        long disciplinaId = inserirDisciplina();
        long assuntoId = inserirAssunto(disciplinaId);

        // primeira revisão PENDENTE — válida, situacao nasce PENDENTE por DEFAULT
        try (var ps = conexao.prepareStatement("""
                INSERT INTO revisao (assunto_id, nivel, data_prevista)
                VALUES (?, 1, CURRENT_DATE)
                """)) {
            ps.setLong(1, assuntoId);
            ps.executeUpdate();
        }

        // segunda revisão PENDENTE do MESMO assunto — é isto que D-05 proíbe
        try (var ps = conexao.prepareStatement("""
                INSERT INTO revisao (assunto_id, nivel, data_prevista)
                VALUES (?, 2, CURRENT_DATE)
                """)) {
            ps.setLong(1, assuntoId);
            ps.executeUpdate();
            fail("segunda revisão PENDENTE do mesmo assunto deveria ter sido recusada");
        } catch (PSQLException e) {
            assertEquals("ux_revisao_d05_pendente_por_assunto", e.getServerErrorMessage().getConstraint());
        }
    }

    // TODO(human): escreva os oito métodos restantes, um por regra:
    //
    //   D-01  | sessao.assunto_id apontando para um id que não existe em
    //         | assunto — violação de FK, não de CHECK. Nome esperado:
    //         | fk_sessao_d01_assunto
    @Test
    void d01_sessaoEstudoIdApontandoIdAssuntoInexistente_recusada() throws SQLException {
        long assuntoIdInexistente = 999_999_999_999L;
        try (var ps = conexao.prepareStatement("""
                INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, tentativa_id)
                VALUES (?, ?, CURRENT_DATE, 1, ?)
                """)) {
            ps.setLong(1, assuntoIdInexistente);
            ps.setString(2, "ESTUDO");
            ps.setObject(3, UUID.randomUUID());
            ps.executeUpdate();
            fail("inserção de um assunto_id inexistente deveria ter sido recusada");
        } catch (PSQLException e) {
            assertEquals("fk_sessao_d01_assunto", e.getServerErrorMessage().getConstraint());
        }
    }
    //   D-02  | sessao tipo='ESTUDO' com resultado preenchido.
    //         | ck_sessao_d02_estudo_sem_resultado
    @Test
    void d02_sessaoEstudoComResultadoPreenchido_recusada() throws SQLException {
        long disciplinaId = inserirDisciplina();
        long assuntoId = inserirAssunto(disciplinaId);
        try (var ps = conexao.prepareStatement("""
            INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, resultado, tentativa_id)
            VALUES (?, ?, CURRENT_DATE, 1, ?, ?)
        """)) {
            ps.setLong(1, assuntoId);
            ps.setString(2, "ESTUDO");
            ps.setObject(3, "SUCESSO");
            ps.setObject(4, UUID.randomUUID());
            ps.executeUpdate();
            fail("inserção de sessão de estudo com resultado preenchido deveria ter sido recusada");
        } catch (PSQLException e) {
            assertEquals("ck_sessao_d02_estudo_sem_resultado", e.getServerErrorMessage().getConstraint());
        }
    }
    //   D-04a | sessao tipo='ESTUDO' com previsao_percentual preenchido
    //         | (ou qualquer combinação fora do que §3.1 permite).
    //         | ck_sessao_d04a_previsao_por_tipo
    @Test
    void d04a_sessaoEstudoComValorPrevisao_recusada() throws SQLException{
        long disciplinaId = inserirDisciplina();
        long assuntoId = inserirAssunto(disciplinaId);
        try (var ps = conexao.prepareStatement("""
            INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, tentativa_id, previsao_percentual)
            VALUES (?, ?, CURRENT_DATE, 1, ?, ?)
        """)) {
            ps.setLong(1, assuntoId);
            ps.setString(2, "ESTUDO");
            ps.setObject(3, UUID.randomUUID());
            ps.setInt(4, 10);
            ps.executeUpdate();
            fail("inserção de sessão de estudo com valor de previsao deveria ter sido recusada");
        } catch (PSQLException e) {
            assertEquals("ck_sessao_d04a_previsao_por_tipo", e.getServerErrorMessage().getConstraint());
        }
    }


    //   D-06  | revisao situacao='CUMPRIDA' com sessao_cumpriu_id nulo.
    //         | ck_revisao_d06_cumprida_tem_sessao
    @Test
    void d06_revisaoCumpridaSemSessao_recusada() throws SQLException{
        long disciplinaId = inserirDisciplina();
        long assuntoId = inserirAssunto(disciplinaId);
        try (var ps = conexao.prepareStatement("""
                INSERT INTO revisao (assunto_id, nivel, data_prevista, situacao)
                VALUES (?, ?, CURRENT_DATE, ?)
                """)) {
            ps.setLong(1, assuntoId);
            ps.setInt(2, 1);
            ps.setString(3, "CUMPRIDA");
            ps.executeUpdate();
            fail("insercao de revisao com situacao cumprida e sem sessao_id deveria ter sido recusada");
        } catch (PSQLException e) {
            assertEquals("ck_revisao_d06_cumprida_tem_sessao", e.getServerErrorMessage().getConstraint());
        }
    }


    //   D-36  | sessao tipo='QUESTOES' com formato nulo.
    //         | ck_sessao_d36_questoes_tem_formato

    @Test
    void d36_sessaoQuestoesSemFormato_recusada() throws SQLException {
        long disciplinaId = inserirDisciplina();
        long assuntoId = inserirAssunto(disciplinaId);
        try (var ps = conexao.prepareStatement("""
            INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, tentativa_id)
            VALUES (?, ? , CURRENT_DATE, ? , ?)
            """)){
                ps.setLong(1, assuntoId);
                ps.setString(2, "QUESTOES");
                ps.setInt(3, 1);
                ps.setObject(4, UUID.randomUUID());
                ps.executeUpdate();
                fail("sessao QUESTOES sem formato deveria ter sido recusada");
        } catch (PSQLException e) {
            assertEquals("ck_sessao_d36_questoes_tem_formato", e.getServerErrorMessage().getConstraint());
        }
    }
    //   D-41  | assunto com ordem nulo.
    //         | ck_assunto_d41_ordem_obrigatoria
    @Test
    void d41_assuntoSemOrdem_recusada() throws SQLException {
        long disciplinaId = inserirDisciplina();
        try (var ps = conexao.prepareStatement("""
            INSERT INTO assunto (disciplina_id, nome, peso, dificuldade_percebida, ativo)
            VALUES (?, ? , ?, ? , true)
            """)){
            ps.setLong(1, disciplinaId);
            ps.setString(2, "BANCO DE DADOS");
            ps.setString(3, "MEDIO");
            ps.setInt(4, 5);
            ps.executeUpdate();
            fail("assunto sem ordem deveria ter sido recusado");
        } catch (PSQLException e) {
            assertEquals("ck_assunto_d41_ordem_obrigatoria", e.getServerErrorMessage().getConstraint());
        }
    }
    //   D-45  | duas sessao com o MESMO tentativa_id (gere um UUID, use nos dois).
    //         | ux_sessao_d45_tentativa_unica
    @Test
    void d45_sessoesComMesmoTentativaId_recusada() throws SQLException {
        long disciplinaId = inserirDisciplina();
        long assuntoId = inserirAssunto(disciplinaId);
        var tentativaId = UUID.randomUUID();
        try (var ps = conexao.prepareStatement("""
            INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, tentativa_id)
            VALUES (?, ? , CURRENT_DATE, ? , ?)
            """)){
            ps.setLong(1, assuntoId);
            ps.setString(2, "ESTUDO");
            ps.setInt(3, 1);
            ps.setObject(4, tentativaId);
            ps.executeUpdate();
        }
        try (var ps = conexao.prepareStatement("""
            INSERT INTO sessao (assunto_id, tipo, data, tempo_minutos, tentativa_id)
            VALUES (?, ? , CURRENT_DATE, ? , ?)
            """)){
            ps.setLong(1, assuntoId);
            ps.setString(2, "ESTUDO");
            ps.setInt(3, 1);
            ps.setObject(4, tentativaId);
            ps.executeUpdate();
            fail("sessao com tentativa_id duplicada deveria ter sido recusada");
        } catch (PSQLException e) {
            assertEquals("ux_sessao_d45_tentativa_unica", e.getServerErrorMessage().getConstraint());
        }
    }
    //   J-1   | dois assunto na MESMA disciplina com nomes que só diferem em
    //         | acento/caixa (ex.: "Direção Constitucional" e
    //         | "direcao constitucional") — é exatamente o que
    //         | unaccent_imutavel(lower(nome)) normaliza para igual.
    //         | ux_assunto_j1_nome_por_disciplina
    //
    @Test
    void j01_assuntoDeMesmaDisciplinaComNomeDuplicado_recusada() throws SQLException {
        long disciplinaId = inserirDisciplina();
        try (var ps = conexao.prepareStatement("""
            INSERT INTO assunto (disciplina_id, nome, ordem, peso, dificuldade_percebida, ativo)
            VALUES (?, ? , ?, ?, ? , true)
            """)){
            ps.setLong(1, disciplinaId);
            ps.setString(2, "Raciocínio Lógico");
            ps.setInt(3, 1);
            ps.setString(4, "MEDIO");
            ps.setInt(5, 4);
            ps.executeUpdate();
        }
        try (var ps = conexao.prepareStatement("""
            INSERT INTO assunto (disciplina_id, nome, ordem, peso, dificuldade_percebida, ativo)
            VALUES (?, ? , ?, ?, ? , true)
            """)){
            ps.setLong(1, disciplinaId);
            ps.setString(2, "RACIOCINIO LOGICO");
            ps.setInt(3, 1);
            ps.setString(4, "MEDIO");
            ps.setInt(5, 4);
            ps.executeUpdate();
            fail("assunto de mesma disciplina com nome duplicado deveria ter sido recusado");
        } catch (PSQLException e) {
            assertEquals("ux_assunto_j1_nome_por_disciplina", e.getServerErrorMessage().getConstraint());
        }
    }


    // sessao precisa de tempo_minutos, data e tentativa_id (UUID, NOT NULL) em
    // toda inserção — mesmo nos testes que não são sobre essas colunas.
}