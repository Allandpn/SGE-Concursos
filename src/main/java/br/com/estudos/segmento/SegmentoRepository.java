package br.com.estudos.segmento;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SegmentoRepository extends JpaRepository<Segmento, Long> {

    // Sprint 10 (docs/SPRINT-10-SEGMENTO.md §3.2): identidade de reimportação
    // — nunca a posição (ordem).
    Optional<Segmento> findByChaveExterna(String chaveExterna);

    /** Os segmentos ativos, na ordem de leitura — o arquivado (D-66) fica só no histórico. */
    List<Segmento> findByAssuntoIdAndArquivadoEmIsNullOrderByOrdemAsc(Long assuntoId);

    /** D-66 — arquiva os segmentos ativos dos assuntos; nada é apagado (D-18). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Segmento s set s.arquivadoEm = :agora where s.assunto.id in :assuntoIds and s.arquivadoEm is null")
    int arquivarDosAssuntos(@Param("assuntoIds") Collection<Long> assuntoIds, @Param("agora") Instant agora);

    // Estado do material (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §5): segmentos
    // por assunto de um conjunto, numa consulta só.
    @Query("""
        select s.assunto.id, count(s) from Segmento s
        where s.assunto.id in :assuntoIds and s.arquivadoEm is null
        group by s.assunto.id
        """)
    List<Object[]> contarPorAssunto(@Param("assuntoIds") Collection<Long> assuntoIds);

    // Exportação (§5 do doc técnico): join fetch pra ler a chaveExterna do
    // assunto sem N+1 (mesmo cuidado de AssuntoRepository.listarParaExportacao).
    @Query("""
        select s from Segmento s
        join fetch s.assunto a
        where s.arquivadoEm is null
        order by a.id, s.ordem
        """)
    List<Segmento> listarParaExportacao();
}
