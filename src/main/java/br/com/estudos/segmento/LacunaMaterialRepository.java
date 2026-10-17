package br.com.estudos.segmento;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LacunaMaterialRepository extends JpaRepository<LacunaMaterial, Long> {

    List<LacunaMaterial> findByAssuntoIdIn(Collection<Long> assuntoIds);
}
