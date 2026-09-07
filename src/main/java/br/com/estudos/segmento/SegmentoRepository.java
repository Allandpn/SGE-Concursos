package br.com.estudos.segmento;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SegmentoRepository extends JpaRepository<Segmento, Long> {

    // Sprint 10 (docs/SPRINT-10-SEGMENTO.md §3.2): identidade de reimportação
    // — nunca a posição (ordem).
    Optional<Segmento> findByChaveExterna(String chaveExterna);

    List<Segmento> findByAssuntoIdOrderByOrdemAsc(Long assuntoId);

    // Exportação (§5 do doc técnico): join fetch pra ler a chaveExterna do
    // assunto sem N+1 (mesmo cuidado de AssuntoRepository.listarParaExportacao).
    @Query("""
        select s from Segmento s
        join fetch s.assunto a
        order by a.id, s.ordem
        """)
    List<Segmento> listarParaExportacao();
}
