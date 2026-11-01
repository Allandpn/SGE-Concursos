package br.com.estudos.pergunta;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.estudos.edital.FalhaIaException;
import br.com.estudos.edital.ServicoIa;
import br.com.estudos.leitura.MarcacaoService;
import tools.jackson.databind.json.JsonMapper;

/**
 * D-122 (docs/SPRINT-32-PERGUNTAS.md §1 e §4; ADR-053): a fila de preparação das perguntas. Registrar o estudo de um
 * bloco enfileira; a varredura completa a fila com os blocos já lidos e prepara alguns por vez, fora de transação
 * (a IA leva dezenas de segundos). IA indisponível nunca vira pergunta nem resultado: o item fica pendente (como D-75).
 */
@Service
public class PreparoPerguntas {

    private static final Logger LOG = LoggerFactory.getLogger(PreparoPerguntas.class);
    static final int BACKFILL_POR_VARREDURA = 5;
    static final int PREPAROS_POR_VARREDURA = 2;
    static final int TENTATIVAS = 2;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transacao;
    private final TextoDoBloco textos;
    private final ServicoIa ia;
    private final MarcacaoService marcacoes;
    private final JsonMapper json;
    private final Clock relogio;

    public PreparoPerguntas(JdbcTemplate jdbc, PlatformTransactionManager transacoes, TextoDoBloco textos, ServicoIa ia,
                            MarcacaoService marcacoes, JsonMapper json, Clock relogio) {
        this.jdbc = jdbc;
        this.transacao = new TransactionTemplate(transacoes);
        this.textos = textos;
        this.ia = ia;
        this.marcacoes = marcacoes;
        this.json = json;
        this.relogio = relogio;
    }

    /** Chamado na transação que grava o estudo do bloco: o bloco foi lido, as perguntas podem nascer. */
    public void enfileirar(long segmentoId) {
        jdbc.update("""
            INSERT INTO pergunta_preparo (segmento_id, situacao, pedido_em, atualizado_em) VALUES (?, 'PENDENTE', ?, ?)
            ON CONFLICT (segmento_id) DO NOTHING""", segmentoId, agora(), agora());
    }

    /** A cada 5 minutos; "-" desliga (os testes chamam com a mão). */
    @Scheduled(cron = "${estudos.perguntas.varredura-cron:0 */5 * * * *}")
    public void varrer() {
        completarFila(BACKFILL_POR_VARREDURA);
        for (int i = 0; i < PREPAROS_POR_VARREDURA; i++) {
            var proximo = proximoPendente();
            if (proximo.isEmpty() || !preparar(proximo.get())) {
                return; // nada na fila, ou a IA está fora: tenta na próxima varredura
            }
        }
    }

    /** Os blocos já lidos sem preparo, os de assunto com revisão pendente mais próxima primeiro (D-122). */
    int completarFila(int quantos) {
        return jdbc.update("""
            INSERT INTO pergunta_preparo (segmento_id, situacao, pedido_em, atualizado_em)
            SELECT g.id, 'PENDENTE', ?, ? FROM segmento g
            WHERE g.arquivado_em IS NULL
              AND EXISTS (SELECT 1 FROM sessao s WHERE s.segmento_id = g.id AND s.tipo = 'ESTUDO' AND s.ativo)
              AND NOT EXISTS (SELECT 1 FROM pergunta_preparo p WHERE p.segmento_id = g.id)
            ORDER BY (SELECT min(r.data_prevista) FROM revisao r WHERE r.assunto_id = g.assunto_id AND r.situacao = 'PENDENTE') NULLS LAST, g.id
            LIMIT ?
            ON CONFLICT (segmento_id) DO NOTHING""", agora(), agora(), quantos);
    }

    Optional<Long> proximoPendente() {
        return jdbc.queryForList("""
            SELECT p.segmento_id FROM pergunta_preparo p JOIN segmento g ON g.id = p.segmento_id
            WHERE p.situacao = 'PENDENTE'
            ORDER BY (SELECT min(r.data_prevista) FROM revisao r WHERE r.assunto_id = g.assunto_id AND r.situacao = 'PENDENTE') NULLS LAST,
                     p.pedido_em, p.segmento_id
            LIMIT 1""", Long.class).stream().findFirst();
    }

    /** Prepara um bloco. Falso quando a IA está fora (o item continua pendente e a varredura para). */
    public boolean preparar(long segmentoId) {
        var bloco = jdbc.query("""
            SELECT g.arquivo, a.nome FROM segmento g JOIN assunto a ON a.id = g.assunto_id WHERE g.id = ?""",
            (rs, n) -> new String[] {rs.getString(1), rs.getString(2)}, segmentoId);
        if (bloco.isEmpty()) {
            marcar(segmentoId, "FALHOU", "Bloco não encontrado.", false);
            return true;
        }
        Optional<TextoDoBloco.Texto> texto;
        try {
            texto = textos.ler(bloco.get(0)[0]);
        } catch (TextoDoBloco.SemArquivo e) {
            marcar(segmentoId, "FALHOU", e.getMessage(), false);
            return true;
        }
        if (texto.isEmpty()) {
            marcar(segmentoId, "SEM_TEXTO", "O PDF do bloco não tem texto (escaneado).", false);
            return true;
        }
        String resposta;
        try {
            resposta = ia.completar(PromptsPerguntas.SISTEMA, PromptsPerguntas.usuario(bloco.get(0)[1], texto.get(), marcacoesDo(segmentoId)));
        } catch (FalhaIaException e) {
            LOG.info("perguntas: IA indisponível para o bloco {}; fica para a próxima varredura ({})", segmentoId, e.getMessage());
            jdbc.update("UPDATE pergunta_preparo SET motivo = ?, atualizado_em = ? WHERE segmento_id = ?", e.getMessage(), agora(), segmentoId);
            return false;
        }
        List<RespostaPerguntas.Pergunta> perguntas;
        try {
            perguntas = RespostaPerguntas.ler(resposta, texto.get().paginas(), json);
        } catch (RespostaPerguntas.Invalida e) {
            var tentativas = jdbc.queryForObject("SELECT tentativas FROM pergunta_preparo WHERE segmento_id = ?", Integer.class, segmentoId) + 1;
            marcar(segmentoId, tentativas >= TENTATIVAS ? "FALHOU" : "PENDENTE", e.getMessage(), true);
            return true;
        }
        gravar(segmentoId, perguntas);
        LOG.info("perguntas: bloco {} pronto com {} perguntas", segmentoId, perguntas.size());
        return true;
    }

    private void gravar(long segmentoId, List<RespostaPerguntas.Pergunta> perguntas) {
        transacao.executeWithoutResult(s -> {
            for (var p : perguntas) {
                long id = jdbc.queryForObject("""
                    INSERT INTO pergunta (segmento_id, tipo, enunciado, origem_marcada, criada_em) VALUES (?, ?, ?, ?, ?) RETURNING id""",
                    Long.class, segmentoId, p.tipo(), p.enunciado(), p.marcada(), agora());
                int ordem = 1;
                for (var pt : p.pontos()) {
                    jdbc.update("INSERT INTO pergunta_ponto (pergunta_id, ordem, texto, principal, pagina) VALUES (?, ?, ?, ?, ?)",
                        id, ordem++, pt.texto(), pt.principal(), pt.pagina());
                }
            }
            jdbc.update("UPDATE pergunta_preparo SET situacao = 'PRONTA', motivo = NULL, atualizado_em = ? WHERE segmento_id = ?", agora(), segmentoId);
        });
    }

    private void marcar(long segmentoId, String situacao, String motivo, boolean contaTentativa) {
        jdbc.update("UPDATE pergunta_preparo SET situacao = ?, motivo = ?, tentativas = tentativas + ?, atualizado_em = ? WHERE segmento_id = ?",
            situacao, motivo, contaTentativa ? 1 : 0, agora(), segmentoId);
        if (!"PENDENTE".equals(situacao)) {
            LOG.warn("perguntas: bloco {} ficou {}: {}", segmentoId, situacao, motivo);
        }
    }

    /** As marcações vigentes do bloco (§3.17), com a nota de cada destaque, para a IA priorizar (D-123). */
    private List<PromptsPerguntas.Marcacao> marcacoesDo(long segmentoId) {
        var vigentes = marcacoes.vigentes(segmentoId);
        var notas = new HashMap<java.util.UUID, String>();
        vigentes.stream().filter(m -> m.tipo() == MarcacaoService.Tipo.NOTA_TRECHO).forEach(m -> notas.put(m.destaqueChave(), m.texto()));
        var saida = new ArrayList<PromptsPerguntas.Marcacao>();
        for (var m : vigentes) {
            if (m.tipo() == MarcacaoService.Tipo.DESTAQUE) {
                saida.add(new PromptsPerguntas.Marcacao(m.cor(), m.citacao(), m.pagina(), notas.get(m.chave())));
            }
        }
        return saida;
    }

    private OffsetDateTime agora() {
        return OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
    }
}
