package br.com.estudos.concurso;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcursoRepository extends JpaRepository<Concurso, Long> {

    Optional<Concurso> findByAtivoTrue();

    Optional<Concurso> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Concurso> findAllByOrderByAtivadoEmDescIdDesc();
}
