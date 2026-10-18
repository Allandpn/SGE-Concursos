package br.com.estudos.prova;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * As restrições da Sprint 24, cada uma com a regra no nome (docs/SPRINT-24-PROVA-GABARITO.md §2, §10):
 * é o banco que garante, não o serviço.
 */
class ProvaEstruturaTest extends ProvaTestBase {

    private void recusa(String restricao, Runnable escrita) {
        var erro = assertThrows(DataAccessException.class, escrita::run);
        assertTrue(String.valueOf(erro.getMessage()).contains(restricao),
            "esperava a restrição " + restricao + " e veio: " + erro.getMessage());
    }

    private long questao(long provaId, int numero, String resposta, String motivo, String atribuicao, Long assuntoId) {
        jdbc.update("""
            insert into questao (prova_id, numero, enunciado, resposta_gabarito, motivo_nao_avaliavel, atribuicao, assunto_id)
            values (?, ?, 'x', ?, ?, ?, ?)""", provaId, numero, resposta, motivo, atribuicao, assuntoId);
        return jdbc.queryForObject("select id from questao where prova_id = ? and numero = ?", Long.class, provaId, numero);
    }

    @Test
    void d81_semGabaritoNaoJulga() throws Exception {
        var prova = prova(alvo(), null);
        recusa("ck_prova_d81_sem_gabarito_nao_julga", () ->
            jdbc.update("update prova set status = 'JULGANDO', formato = 'MULTIPLA_ESCOLHA' where id = ?", prova.getId()));
        jdbc.update("update prova set status = 'JULGANDO', formato = 'MULTIPLA_ESCOLHA', gabarito_situacao = 'PRELIMINAR' where id = ?", prova.getId());
    }

    @Test
    void d74_foraDoEditalNaoApontaAssunto() throws Exception {
        var alvo = alvo();
        var prova = prova(alvo, null);
        recusa("ck_questao_d74_fora_do_edital_sem_assunto", () ->
            questao(prova.getId(), 1, "A", null, "FORA_DO_EDITAL", assuntoId(alvo.chave(1))));
        recusa("ck_questao_atribuida_tem_assunto", () ->
            questao(prova.getId(), 2, "A", null, "AUTOMATICA", null));
        questao(prova.getId(), 3, "A", null, "FORA_DO_EDITAL", null);
    }

    @Test
    void d70_questaoAvaliavelTemGabarito_emotivoValido() throws Exception {
        var prova = prova(alvo(), null);
        recusa("ck_questao_d70_avaliavel_tem_gabarito", () -> questao(prova.getId(), 1, null, null, "PENDENTE", null));
        recusa("ck_questao_d70_motivo", () -> questao(prova.getId(), 2, "A", "INVENTADO", "PENDENTE", null));
        questao(prova.getId(), 3, null, "ANULADA", "PENDENTE", null);
    }

    @Test
    void d68_respondivelExigeSustentacao() throws Exception {
        var prova = prova(alvo(), null);
        var questao = questao(prova.getId(), 1, "A", null, "PENDENTE", null);
        recusa("ck_veredito_d68_sustentacao", () ->
            jdbc.update("insert into veredito_questao (questao_id, veredito, gabarito_considerado) values (?, 'RESPONDIVEL', 'A')", questao));
        jdbc.update("insert into veredito_questao (questao_id, veredito, gabarito_considerado) values (?, 'NAO_RESPONDIVEL', 'A')", questao);
    }

    @Test
    void d73_vereditoNaoMudaNemSome() throws Exception {
        var prova = prova(alvo(), null);
        var questao = questao(prova.getId(), 1, "A", null, "PENDENTE", null);
        jdbc.update("insert into veredito_questao (questao_id, veredito, gabarito_considerado) values (?, 'PARCIAL', 'A')", questao);
        recusa("tg_veredito_questao_d73_imutavel", () ->
            jdbc.update("update veredito_questao set veredito = 'NAO_RESPONDIVEL' where questao_id = ?", questao));
        recusa("tg_veredito_questao_d73_imutavel", () ->
            jdbc.update("delete from veredito_questao where questao_id = ?", questao));
    }

    @Test
    void d80_mesmaTentativaContaUmaVez() throws Exception {
        var prova = prova(alvo(), null);
        var tentativa = UUID.randomUUID();
        jdbc.update("insert into tentativa_prova (prova_id, tentativa_id) values (?, ?)", prova.getId(), tentativa);
        recusa("ux_tentativa_prova_d80_tentativa", () ->
            jdbc.update("insert into tentativa_prova (prova_id, tentativa_id) values (?, ?)", prova.getId(), tentativa));
    }

    @Test
    void d78_soUmaTentativaPorProvaAlimentaM1() throws Exception {
        var prova = prova(alvo(), null);
        // Os simulados são do banco compartilhado: o teste os remove no fim, senão SimuladoDominioTest os conta.
        jdbc.update("insert into simulado (data, duracao_minutos) values (current_date, 60)");
        jdbc.update("insert into simulado (data, duracao_minutos) values (current_date, 60)");
        var simulados = jdbc.queryForList("select id from simulado order by id desc limit 2", Long.class);
        try {
            jdbc.update("insert into tentativa_prova (prova_id, tentativa_id, entregue_em, simulado_id) values (?, ?, now(), ?)",
                prova.getId(), UUID.randomUUID(), simulados.get(0));
            recusa("ux_tentativa_prova_d78_uma_conta", () ->
                jdbc.update("insert into tentativa_prova (prova_id, tentativa_id, entregue_em, simulado_id) values (?, ?, now(), ?)",
                    prova.getId(), UUID.randomUUID(), simulados.get(1)));
            // As que não contam (sem simulado) podem ser quantas forem.
            jdbc.update("insert into tentativa_prova (prova_id, tentativa_id, entregue_em) values (?, ?, now())", prova.getId(), UUID.randomUUID());
            jdbc.update("insert into tentativa_prova (prova_id, tentativa_id, entregue_em) values (?, ?, now())", prova.getId(), UUID.randomUUID());
        } finally {
            jdbc.update("delete from tentativa_prova where prova_id = ?", prova.getId());
            simulados.forEach(id -> jdbc.update("delete from simulado where id = ?", id));
        }
    }

    @Test
    void mesmaProvaAoMesmoConcurso_eUmaSo() throws Exception {
        var alvo = alvo();
        var hash = "h".repeat(64);
        var a = provaService.registrar(alvo.concursoId(), "a.pdf", hash, null, null);
        var b = provaService.registrar(alvo.concursoId(), "b.pdf", hash, null, null);
        org.junit.jupiter.api.Assertions.assertEquals(a.getId(), b.getId(), "idempotência do envio");
    }
}
