package br.com.estudos.prova;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RespostaProvaRepository extends JpaRepository<RespostaProva, Long> {

    List<RespostaProva> findByTentativaId(Long tentativaId);

    Optional<RespostaProva> findByTentativaIdAndQuestaoId(Long tentativaId, Long questaoId);
}
