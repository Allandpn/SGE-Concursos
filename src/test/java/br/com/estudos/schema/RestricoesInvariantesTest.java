package br.com.estudos.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.sql.SQLException;

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
    //   D-02  | sessao tipo='ESTUDO' com resultado preenchido.
    //         | ck_sessao_d02_estudo_sem_resultado
    //   D-04a | sessao tipo='ESTUDO' com previsao_percentual preenchido
    //         | (ou qualquer combinação fora do que §3.1 permite).
    //         | ck_sessao_d04a_previsao_por_tipo
    //   D-06  | revisao situacao='CUMPRIDA' com sessao_cumpriu_id nulo.
    //         | ck_revisao_d06_cumprida_tem_sessao
    //   D-36  | sessao tipo='QUESTOES' com formato nulo.
    //         | ck_sessao_d36_questoes_tem_formato
    //   D-41  | assunto com ordem nulo.
    //         | ck_assunto_d41_ordem_obrigatoria
    //   D-45  | duas sessao com o MESMO tentativa_id (gere um UUID, use nos dois).
    //         | ux_sessao_d45_tentativa_unica
    //   J-1   | dois assunto na MESMA disciplina com nomes que só diferem em
    //         | acento/caixa (ex.: "Direção Constitucional" e
    //         | "direcao constitucional") — é exatamente o que
    //         | unaccent_imutavel(lower(nome)) normaliza para igual.
    //         | ux_assunto_j1_nome_por_disciplina
    //
    // sessao precisa de tempo_minutos, data e tentativa_id (UUID, NOT NULL) em
    // toda inserção — mesmo nos testes que não são sobre essas colunas.
}