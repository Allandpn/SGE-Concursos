package br.com.estudos.prova;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VereditoQuestaoRepository extends JpaRepository<VereditoQuestao, Long> {

    /** Os vereditos vigentes de uma prova: o de maior id de cada questão (D-73), numa consulta. */
    @Query("""
        select v from VereditoQuestao v
        where v.id in (
            select max(v2.id) from VereditoQuestao v2
            where v2.questao.prova.id = :provaId
            group by v2.questao.id)
        """)
    List<VereditoQuestao> vigentesDaProva(@Param("provaId") Long provaId);

    /** O veredito vigente é o de maior id (D-73): nada o marca como vigente (D-16). */
    Optional<VereditoQuestao> findFirstByQuestaoIdOrderByIdDesc(Long questaoId);
}
