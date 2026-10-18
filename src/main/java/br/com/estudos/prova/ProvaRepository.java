package br.com.estudos.prova;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.estudos.shared.enums.StatusProva;

public interface ProvaRepository extends JpaRepository<Prova, Long> {

    /** Idempotência do envio: a mesma prova ao mesmo concurso é uma só. */
    Optional<Prova> findByConcursoIdAndHashProva(Long concursoId, String hashProva);

    List<Prova> findByStatusInOrderByIdAsc(Collection<StatusProva> status);

    List<Prova> findByConcursoIdOrderByIdDesc(Long concursoId);
}
