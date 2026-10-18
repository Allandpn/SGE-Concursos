package br.com.estudos.prova;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestaoRepository extends JpaRepository<Questao, Long> {

    List<Questao> findByProvaIdOrderByNumeroAsc(Long provaId);

    Optional<Questao> findByProvaIdAndNumero(Long provaId, Integer numero);

    boolean existsByProvaId(Long provaId);

    /**
     * D-79 e D-84 — as questões a julgar, numa consulta, derivada dos segmentos (D-16):
     * avaliável, com assunto, e uma destas: não tem veredito; o veredito vigente é anterior à
     * última mudança do material do assunto (segmento criado ou arquivado depois); ou a
     * resposta do gabarito mudou desde o julgamento. Sem coluna nova, sem "sujo".
     * docs/SPRINT-24-PROVA-GABARITO.md §5.
     */
    @Query(value = """
        SELECT q.id FROM questao q
        WHERE q.prova_id = :provaId
          AND q.motivo_nao_avaliavel IS NULL
          AND q.assunto_id IS NOT NULL
          AND (
            NOT EXISTS (SELECT 1 FROM veredito_questao v WHERE v.questao_id = q.id)
            OR EXISTS (
              SELECT 1 FROM veredito_questao v
              WHERE v.questao_id = q.id
                AND v.id = (SELECT max(v2.id) FROM veredito_questao v2 WHERE v2.questao_id = q.id)
                AND (
                  v.gabarito_considerado IS DISTINCT FROM q.resposta_gabarito
                  OR EXISTS (
                    SELECT 1 FROM segmento s
                    WHERE s.assunto_id = q.assunto_id
                      AND greatest(s.criado_em, coalesce(s.arquivado_em, s.criado_em)) > v.criado_em
                  )
                )
            )
          )
        ORDER BY q.numero
        """, nativeQuery = true)
    List<Long> idsAJulgar(@Param("provaId") Long provaId);
}
