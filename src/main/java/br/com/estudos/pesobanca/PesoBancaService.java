package br.com.estudos.pesobanca;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.exception.NotFoundException;

/** O peso pela banca (01_DOMINIO §3.23; D-145, D-146, D-147; ADR-057; docs/SPRINT-22-PESO-PELA-BANCA.md §2). */
@Service
public class PesoBancaService {

    public record Proposta(long assuntoId, String nome, TipoPeso pesoEdital, TipoPeso pesoAtual, TipoPeso proposto, int questoes,
        boolean sobeParaAlto) {}

    public record DisciplinaBanca(long disciplinaId, String nome, int provas, int questoes, boolean suficiente, List<Proposta> propostas) {}

    public record Ajustado(long assuntoId, String nome, TipoPeso pesoEdital, TipoPeso peso, Instant aceitoEm, TipoPeso propostaHoje) {}

    public record Situacao(String banca, int provas, int questoes, Integer anoMinimo, Integer anoMaximo, int totalPropostas,
        List<DisciplinaBanca> disciplinas, List<Ajustado> ajustados) {}

    private record Coberto(long assuntoId, String nome, long disciplinaId, String disciplina, TipoPeso pesoEdital) {}

    private record Prova(long id, String banca, Integer ano) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PesoBancaService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** D-147: os ajustes vigentes do concurso, assunto → peso. Usado em toda aplicação da cobertura (ADR-057). */
    @Transactional(readOnly = true)
    public Map<Long, TipoPeso> vigentes(Long concursoId) {
        var vigentes = new HashMap<Long, TipoPeso>();
        if (concursoId == null) return vigentes;
        jdbc.query("SELECT assunto_id, peso FROM peso_banca WHERE concurso_id = ? AND desfeito_em IS NULL",
            rs -> { vigentes.put(rs.getLong(1), TipoPeso.valueOf(rs.getString(2))); }, concursoId);
        return vigentes;
    }

    @Transactional(readOnly = true)
    public Situacao situacao(long concursoId) {
        var concurso = jdbc.query("SELECT banca FROM concurso WHERE id = ?", (rs, n) -> new String[] {rs.getString(1)}, concursoId);
        if (concurso.isEmpty()) {
            throw new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE");
        }
        // D-145: a banca do concurso; sem ela, a da prova hospedada mais recente dele.
        var banca = concurso.get(0)[0];
        if (banca == null || PesoBanca.normalizar(banca).isEmpty()) {
            banca = jdbc.queryForList("SELECT banca FROM prova WHERE concurso_id = ? AND banca IS NOT NULL ORDER BY id DESC LIMIT 1",
                String.class, concursoId).stream().findFirst().orElse(null);
        }
        var cobertos = jdbc.query("""
            SELECT ca.assunto_id, a.nome, ca.disciplina_id, d.nome, ca.peso
            FROM concurso_assunto ca JOIN assunto a ON a.id = ca.assunto_id JOIN disciplina d ON d.id = ca.disciplina_id
            WHERE ca.concurso_id = ? AND a.ativo ORDER BY d.nome, ca.ordem""",
            (rs, n) -> new Coberto(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), TipoPeso.valueOf(rs.getString(5))),
            concursoId);
        var vigentes = vigentes(concursoId);
        var aceitos = new HashMap<Long, Instant>();
        jdbc.query("SELECT assunto_id, aceito_em FROM peso_banca WHERE concurso_id = ? AND desfeito_em IS NULL",
            rs -> { aceitos.put(rs.getLong(1), rs.getTimestamp(2).toInstant()); }, concursoId);

        var provas = provasDaBanca(banca);
        var idsProvas = provas.stream().map(Prova::id).toList();
        // As questões avaliáveis com assunto, por assunto: total e provas distintas (D-145).
        var questoesPorAssunto = new HashMap<Long, Integer>();
        var provasPorAssunto = new HashMap<Long, Set<Long>>();
        if (!idsProvas.isEmpty() && !cobertos.isEmpty()) {
            jdbc.query("""
                SELECT assunto_id, prova_id, count(*) FROM questao
                WHERE prova_id = ANY (?) AND assunto_id = ANY (?) AND motivo_nao_avaliavel IS NULL
                GROUP BY assunto_id, prova_id""",
                rs -> {
                    questoesPorAssunto.merge(rs.getLong(1), rs.getInt(3), Integer::sum);
                    provasPorAssunto.computeIfAbsent(rs.getLong(1), k -> new HashSet<>()).add(rs.getLong(2));
                },
                (Object) idsProvas.toArray(Long[]::new), (Object) cobertos.stream().map(Coberto::assuntoId).toArray(Long[]::new));
        }
        var provasMinimas = parametro("banca_provas_minimas");
        var questoesMinimas = parametro("banca_questoes_minimas");

        var porDisciplina = new LinkedHashMap<Long, List<Coberto>>();
        cobertos.forEach(c -> porDisciplina.computeIfAbsent(c.disciplinaId(), k -> new ArrayList<>()).add(c));
        var propostaHoje = new HashMap<Long, TipoPeso>();
        var disciplinas = new ArrayList<DisciplinaBanca>();
        var provasUsadas = new HashSet<Long>();
        var totalQuestoes = 0;
        for (var lista : porDisciplina.values()) {
            var provasDaDisciplina = new HashSet<Long>();
            var questoes = 0;
            for (var c : lista) {
                questoes += questoesPorAssunto.getOrDefault(c.assuntoId(), 0);
                provasDaDisciplina.addAll(provasPorAssunto.getOrDefault(c.assuntoId(), Set.of()));
            }
            provasUsadas.addAll(provasDaDisciplina);
            totalQuestoes += questoes;
            var suficiente = banca != null && provasDaDisciplina.size() >= provasMinimas && questoes >= questoesMinimas;
            var propostas = new ArrayList<Proposta>();
            if (suficiente) {
                var proposto = PesoBanca.propor(lista.stream()
                    .map(c -> new PesoBanca.Linha(c.assuntoId(), c.pesoEdital(), questoesPorAssunto.getOrDefault(c.assuntoId(), 0)))
                    .toList());
                for (var c : lista) {
                    var p = proposto.get(c.assuntoId());
                    propostaHoje.put(c.assuntoId(), p);
                    var atual = vigentes.getOrDefault(c.assuntoId(), c.pesoEdital());
                    if (p != atual) {
                        propostas.add(new Proposta(c.assuntoId(), c.nome(), c.pesoEdital(), atual, p,
                            questoesPorAssunto.getOrDefault(c.assuntoId(), 0), p == TipoPeso.ALTO));
                    }
                }
            }
            disciplinas.add(new DisciplinaBanca(lista.get(0).disciplinaId(), lista.get(0).disciplina(), provasDaDisciplina.size(), questoes,
                suficiente, propostas));
        }
        var ajustados = cobertos.stream().filter(c -> vigentes.containsKey(c.assuntoId()))
            .map(c -> new Ajustado(c.assuntoId(), c.nome(), c.pesoEdital(), vigentes.get(c.assuntoId()), aceitos.get(c.assuntoId()),
                propostaHoje.get(c.assuntoId())))
            .toList();
        var anos = provas.stream().filter(p -> provasUsadas.contains(p.id()) && p.ano() != null).map(Prova::ano).toList();
        return new Situacao(banca, provasUsadas.size(), totalQuestoes,
            anos.stream().min(Integer::compare).orElse(null), anos.stream().max(Integer::compare).orElse(null),
            disciplinas.stream().mapToInt(d -> d.propostas().size()).sum(), disciplinas, ajustados);
    }

    /** D-145: as provas hospedadas da mesma banca, de qualquer concurso, dos últimos N anos (sem ano conta). */
    private List<Prova> provasDaBanca(String banca) {
        if (banca == null) return List.of();
        var anoMinimo = LocalDate.now(clock).getYear() - parametro("banca_anos");
        return jdbc.query("SELECT id, banca, ano FROM prova WHERE banca IS NOT NULL",
                (rs, n) -> new Prova(rs.getLong(1), rs.getString(2), (Integer) rs.getObject(3, Integer.class))).stream()
            .filter(p -> PesoBanca.mesmaBanca(banca, p.banca()))
            .filter(p -> p.ano() == null || p.ano() >= anoMinimo)
            .toList();
    }

    /**
     * D-147: aceita o que é proposta agora entre os assuntos pedidos. Fecha o ajuste vigente; grava outro se o proposto não é o peso do
     * edital; se o concurso é o alvo, o peso vai ao assunto.
     */
    @Transactional
    public Situacao aceitar(long concursoId, Collection<Long> assuntoIds) {
        var pedidos = new HashSet<>(assuntoIds == null ? List.<Long>of() : assuntoIds);
        var alvo = Boolean.TRUE.equals(jdbc.queryForObject("SELECT ativo FROM concurso WHERE id = ?", Boolean.class, concursoId));
        for (var d : situacao(concursoId).disciplinas()) {
            for (var p : d.propostas()) {
                if (!pedidos.contains(p.assuntoId())) continue;
                jdbc.update("UPDATE peso_banca SET desfeito_em = now() WHERE concurso_id = ? AND assunto_id = ? AND desfeito_em IS NULL",
                    concursoId, p.assuntoId());
                if (p.proposto() != p.pesoEdital()) {
                    jdbc.update("INSERT INTO peso_banca (concurso_id, assunto_id, peso) VALUES (?, ?, ?)", concursoId, p.assuntoId(),
                        p.proposto().name());
                }
                if (alvo) {
                    jdbc.update("UPDATE assunto SET peso = ?, atualizado_em = now() WHERE id = ?", p.proposto().name(), p.assuntoId());
                }
            }
        }
        return situacao(concursoId);
    }

    /** D-147: desfazer volta ao peso do edital (o da cobertura). */
    @Transactional
    public Situacao desfazer(long concursoId, long assuntoId) {
        jdbc.update("UPDATE peso_banca SET desfeito_em = now() WHERE concurso_id = ? AND assunto_id = ? AND desfeito_em IS NULL",
            concursoId, assuntoId);
        jdbc.update("""
            UPDATE assunto a SET peso = ca.peso, atualizado_em = now() FROM concurso_assunto ca JOIN concurso c ON c.id = ca.concurso_id
            WHERE ca.concurso_id = ? AND ca.assunto_id = ? AND a.id = ca.assunto_id AND c.ativo""", concursoId, assuntoId);
        return situacao(concursoId);
    }

    private int parametro(String chave) {
        return Integer.parseInt(jdbc.queryForObject("SELECT valor FROM parametro WHERE chave = ?", String.class, chave));
    }
}
