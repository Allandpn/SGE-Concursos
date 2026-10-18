package br.com.estudos.prova;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TentativaProvaRepository extends JpaRepository<TentativaProva, Long> {

    /** D-80 — a tentativa é reconhecida pelo identificador que o cliente gerou. */
    Optional<TentativaProva> findByTentativaId(UUID tentativaId);

    /** D-78 — a única tentativa da prova que alimenta M-1, se houver. */
    Optional<TentativaProva> findFirstByProvaIdAndSimuladoIsNotNull(Long provaId);

    boolean existsByProvaIdAndSimuladoIsNotNull(Long provaId);

    List<TentativaProva> findByProvaIdOrderByIdAsc(Long provaId);
}
