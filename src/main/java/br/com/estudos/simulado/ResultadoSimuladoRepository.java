package br.com.estudos.simulado;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.estudos.metrica.AcertoLinha;

public interface ResultadoSimuladoRepository extends JpaRepository<ResultadoSimulado, Long> {

    List<ResultadoSimulado> findBySimuladoId(Long simuladoId);

    // GET /api/simulados (docs/SPRINT-8-SIMULADO.md §5): busca os resultados
    // de todos os simulados listados numa consulta só, em vez de uma por
    // simulado (N+1 — mesmo "suspeito de sempre" de LAZY sem fetch em lote).
    // join fetch em disciplina (ADR-034): SimuladoMapper lê disciplina.id;
    // sem isto seria N+1 de novo, um SELECT por disciplina distinta.
    @Query("""
        select rs from ResultadoSimulado rs
        join fetch rs.simulado sim
        join fetch rs.disciplina d
        where rs.simulado.id in :simuladoIds
        """)
    List<ResultadoSimulado> findBySimuladoIdIn(@Param("simuladoIds") Collection<Long> simuladoIds);

    // M-1 (docs/SPRINT-8-SIMULADO.md §4): mesma projeção de SessaoRepository
    // .listarAcertoQuestoes — simulado soma no mesmo agregado (§0).
    @Query("""
        select rs.disciplina.id as disciplinaId, rs.formato as formato,
               rs.questoesCorretas as questoesCorretas, rs.questoesTotal as questoesTotal
        from ResultadoSimulado rs
        join rs.simulado s
        where s.data between :inicio and :fim
        """)
    List<AcertoLinha> listarAcerto(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);
}
