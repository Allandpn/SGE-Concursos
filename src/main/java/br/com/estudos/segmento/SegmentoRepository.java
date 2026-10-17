package br.com.estudos.segmento;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SegmentoRepository extends JpaRepository<Segmento, Long> {

    // Sprint 10 (docs/SPRINT-10-SEGMENTO.md §3.2): identidade de reimportação
    // — nunca a posição (ordem).
    Optional<Segmento> findByChaveExterna(String chaveExterna);

    List<Segmento> findByAssuntoIdOrderByOrdemAsc(Long assuntoId);

    // Estado do material (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §5): segmentos
    // por assunto de um conjunto, numa consulta só.
    @Query("""
        select s.assunto.id, count(s) from Segmento s
        where s.assunto.id in :assuntoIds
        group by s.assunto.id
        """)
    List<Object[]> contarPorAssunto(@Param("assuntoIds") Collection<Long> assuntoIds);

    // Exportação (§5 do doc técnico): join fetch pra ler a chaveExterna do
    // assunto sem N+1 (mesmo cuidado de AssuntoRepository.listarParaExportacao).
    @Query("""
        select s from Segmento s
        join fetch s.assunto a
        order by a.id, s.ordem
        """)
    List<Segmento> listarParaExportacao();
}
