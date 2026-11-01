package br.com.estudos.retafinal;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.frente.FrenteService;
import br.com.estudos.shared.enums.FaseAssunto;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.NotFoundException;

/** A reta final (01_DOMINIO §3.22; D-140, D-141, D-143; ADR-056; docs/SPRINT-21-RETA-FINAL.md §2). */
@Service
public class RetaFinalService {

    public record Sugestao(long concursoId, String nome, LocalDate dataProva, long dias, long semanas) {}

    public record Situacao(RetaFinalConsulta.Ativa ativa, Sugestao sugestao) {}

    public record Entrada(int antecipadas, LocalDate ate) {}

    /** Os últimos 7 dias antes da prova ficam livres da manutenção antecipada (D-143). */
    static final int DIAS_LIVRES_ANTES_DA_PROVA = 7;

    private final JdbcTemplate jdbc;
    private final RetaFinalConsulta consulta;
    private final FrenteService frente;
    private final AssuntoRepository assuntos;
    private final Clock clock;

    public RetaFinalService(JdbcTemplate jdbc, RetaFinalConsulta consulta, FrenteService frente, AssuntoRepository assuntos, Clock clock) {
        this.jdbc = jdbc;
        this.consulta = consulta;
        this.frente = frente;
        this.assuntos = assuntos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Situacao situacao() {
        var ativa = consulta.ativa();
        return new Situacao(ativa.orElse(null), ativa.isPresent() ? null : sugestao());
    }

    /**
     * D-140: o alvo com data de prova futura a até N semanas, sem "agora não" e sem reta final desde a última ativação dele (quem saiu
     * decidiu; a sugestão não insiste).
     */
    private Sugestao sugestao() {
        var hoje = LocalDate.now(clock);
        var semanas = jdbc.queryForObject("SELECT valor FROM parametro WHERE chave = 'reta_final_semanas_aviso'", Integer.class);
        return jdbc.query("""
            SELECT c.id, c.nome, c.data_prova FROM concurso c
            WHERE c.ativo AND c.data_prova > ? AND c.data_prova <= ? AND c.reta_final_dispensada_em IS NULL
              AND NOT EXISTS (SELECT 1 FROM reta_final r WHERE r.concurso_id = c.id
                              AND (c.ativado_em IS NULL OR r.entrou_em >= c.ativado_em))""",
            (rs, n) -> {
                var prova = rs.getObject(3, LocalDate.class);
                var dias = ChronoUnit.DAYS.between(hoje, prova);
                return new Sugestao(rs.getLong(1), rs.getString(2), prova, dias, (dias + 6) / 7);
            }, hoje, hoje.plusWeeks(semanas)).stream().findFirst().orElse(null);
    }

    /** D-140, D-141, D-143: entra, fecha o que já não vale e antecipa a manutenção de peso alto. */
    @Transactional
    public Entrada entrar(long concursoId) {
        var hoje = LocalDate.now(clock);
        var concurso = jdbc.query("SELECT ativo, data_prova FROM concurso WHERE id = ?",
            (rs, n) -> new Object[] {rs.getBoolean(1), rs.getObject(2, LocalDate.class)}, concursoId);
        if (concurso.isEmpty()) {
            throw new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE");
        }
        if (!(Boolean) concurso.get(0)[0]) {
            throw new BusinessRuleException("A reta final é só do concurso-alvo.", "NAO_E_O_ALVO");
        }
        var prova = (LocalDate) concurso.get(0)[1];
        if (prova == null || !prova.isAfter(hoje)) {
            throw new BusinessRuleException("Sem data de prova futura não há reta final.", "SEM_DATA_DE_PROVA");
        }
        if (consulta.ativa().isPresent()) {
            return new Entrada(0, null);
        }
        // Aberta de uma ativação anterior (trocou de alvo e voltou): já não vale; fecha antes de abrir outra (D-141).
        jdbc.update("UPDATE reta_final SET saiu_em = now() WHERE concurso_id = ? AND saiu_em IS NULL", concursoId);
        jdbc.update("INSERT INTO reta_final (concurso_id) VALUES (?)", concursoId);
        return antecipar(hoje, prova);
    }

    /**
     * D-143: a manutenção pendente dos consolidados de peso alto que venceria depois de prova − 7 dias vai, pela ordem da data, para
     * amanhã + ⌊i × dias ÷ n⌋. Sem dia entre amanhã e prova − 7, nada é antecipado. A data anterior fica guardada.
     */
    private Entrada antecipar(LocalDate hoje, LocalDate prova) {
        var amanha = hoje.plusDays(1);
        var limite = prova.minusDays(DIAS_LIVRES_ANTES_DA_PROVA);
        if (limite.isBefore(amanha)) {
            return new Entrada(0, null);
        }
        var altos = assuntos.listarAtivosDeDisciplinasAtivas().stream().filter(a -> a.getPeso() == TipoPeso.ALTO).toList();
        var fases = frente.fasesDe(altos);
        var consolidados = altos.stream().filter(a -> fases.get(a.getId()) == FaseAssunto.CONSOLIDADO).map(a -> a.getId()).toList();
        if (consolidados.isEmpty()) {
            return new Entrada(0, limite);
        }
        var revisoes = jdbc.queryForList("""
            SELECT id FROM revisao WHERE situacao = 'PENDENTE' AND data_prevista > ? AND assunto_id = ANY (?)
            ORDER BY data_prevista, id""", Long.class, limite, consolidados.toArray(Long[]::new));
        var dias = ChronoUnit.DAYS.between(amanha, limite) + 1;
        var n = revisoes.size();
        var datas = new ArrayList<Object[]>();
        for (var i = 0; i < n; i++) {
            datas.add(new Object[] {amanha.plusDays(i * dias / n), revisoes.get(i)});
        }
        jdbc.batchUpdate("""
            UPDATE revisao SET data_prevista_antes_reta_final = data_prevista, data_prevista = ?, versao = versao + 1, atualizado_em = now()
            WHERE id = ?""", datas);
        return new Entrada(n, limite);
    }

    /** D-141: "Sair da reta final". As antecipações ficam (ADR-056). */
    @Transactional
    public void sair(long concursoId) {
        jdbc.update("UPDATE reta_final SET saiu_em = now() WHERE concurso_id = ? AND saiu_em IS NULL", concursoId);
    }

    /** D-140: "Agora não" cala a sugestão daquele concurso. */
    @Transactional
    public void dispensar(long concursoId) {
        if (jdbc.update("UPDATE concurso SET reta_final_dispensada_em = now() WHERE id = ?", concursoId) == 0) {
            throw new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE");
        }
    }
}
