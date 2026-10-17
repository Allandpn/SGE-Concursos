package br.com.estudos.segmento;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LacunaMaterialRepository extends JpaRepository<LacunaMaterial, Long> {

    List<LacunaMaterial> findByAssuntoIdInAndDescartadaEmIsNull(Collection<Long> assuntoIds);

    /** D-66 — descarta as lacunas vigentes dos assuntos; nada é apagado (D-18). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LacunaMaterial l set l.descartadaEm = :agora where l.assuntoId in :assuntoIds and l.descartadaEm is null")
    int descartarDosAssuntos(@Param("assuntoIds") Collection<Long> assuntoIds, @Param("agora") Instant agora);
}
