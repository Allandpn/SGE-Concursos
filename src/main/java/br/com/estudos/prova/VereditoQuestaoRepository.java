package br.com.estudos.prova;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VereditoQuestaoRepository extends JpaRepository<VereditoQuestao, Long> {

    /** O veredito vigente é o de maior id (D-73): nada o marca como vigente (D-16). */
    Optional<VereditoQuestao> findFirstByQuestaoIdOrderByIdDesc(Long questaoId);
}
