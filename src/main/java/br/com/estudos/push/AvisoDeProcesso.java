package br.com.estudos.push;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import br.com.estudos.push.PushService.Aviso;
import br.com.estudos.segmento.MaterialService;

/**
 * Os dois avisos de processo concluído (D-96, docs/SPRINT-27 §1): a conferência ficou pronta e o material ficou pronto.
 * Cada um sai no máximo uma vez por análise e rodada: a marca em {@code analise_edital} diz que já saiu, e refazer o
 * material ou reprocessar o edital a zera. O envio é depois do commit e fora dele: o aviso nunca desfaz a entrega que o causou.
 */
@Component
public class AvisoDeProcesso {

    private final JdbcTemplate jdbc;
    private final PushService push;
    private final MaterialService material;

    public AvisoDeProcesso(JdbcTemplate jdbc, PushService push, MaterialService material) {
        this.jdbc = jdbc;
        this.push = push;
        this.material = material;
    }

    /** A leitura do edital foi entregue e a conferência espera a pessoa. */
    public void conferenciaPronta(Long analiseId) {
        if (!marcar(analiseId, "aviso_conferencia_em")) return;
        enviarDepoisDoCommit(new Aviso("Edital lido", nome(analiseId) + ": pronto para conferir.", "/#/concursos/analise/" + analiseId + "/conferencia"));
    }

    /** Chamado depois de cada entrega do material: só avisa quando não resta nada a preparar. */
    public void materialPronto(Long analiseId) {
        List<Long> ids = jdbc.queryForList("""
            SELECT a.assunto_id FROM edital_assunto a JOIN edital_disciplina d ON d.id = a.disciplina_id
             WHERE d.analise_id = ? AND a.aparece AND a.assunto_id IS NOT NULL""", Long.class, analiseId);
        if (ids.isEmpty()) return;
        var resumo = material.resumo(ids);
        if (resumo.aPreparar() > 0 || resumo.prontos() + resumo.semMaterial() == 0) return;
        if (!marcar(analiseId, "aviso_material_em")) return;
        var corpo = nome(analiseId) + ": " + resumo.prontos() + (resumo.prontos() == 1 ? " assunto com material" : " assuntos com material")
            + (resumo.semMaterial() > 0 ? ", " + resumo.semMaterial() + " sem material no acervo." : ".");
        enviarDepoisDoCommit(new Aviso("Material pronto", corpo, "/#/concursos"));
    }

    /** Refazer o material abre uma rodada nova: o próximo "material pronto" volta a avisar. */
    public void novaRodadaDoMaterial(Long concursoId) {
        jdbc.update("UPDATE analise_edital SET aviso_material_em = NULL WHERE concurso_id = ?", concursoId);
    }

    /** Reprocessar o edital abre uma rodada nova da conferência. */
    public void novaRodadaDaConferencia(Long analiseId) {
        jdbc.update("UPDATE analise_edital SET aviso_conferencia_em = NULL WHERE id = ?", analiseId);
    }

    private boolean marcar(Long analiseId, String coluna) {
        return jdbc.update("UPDATE analise_edital SET " + coluna + " = now() WHERE id = ? AND " + coluna + " IS NULL", analiseId) == 1;
    }

    private String nome(Long analiseId) {
        var nomes = jdbc.queryForList("SELECT concurso_nome FROM analise_edital WHERE id = ?", String.class, analiseId);
        return nomes.isEmpty() || nomes.get(0) == null || nomes.get(0).isBlank() ? "O edital" : nomes.get(0);
    }

    private void enviarDepoisDoCommit(Aviso aviso) {
        Runnable enviar = () -> Thread.startVirtualThread(() -> push.enviar(aviso));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar.run();
                }
            });
        } else {
            enviar.run();
        }
    }
}
