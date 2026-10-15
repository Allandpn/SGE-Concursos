package br.com.estudos.concurso;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CoberturaEditalRepository extends JpaRepository<CoberturaEdital, Long> {

    // join fetch: reativar lê assunto e disciplina de cada linha (sem N+1).
    @Query("""
        select c from CoberturaEdital c
        join fetch c.assunto
        join fetch c.disciplina
        where c.concurso.id = :concursoId
        order by c.disciplina.id, c.ordem
        """)
    List<CoberturaEdital> listarDoConcurso(Long concursoId);

    /** Pares (concurso, assunto) de toda cobertura — o histórico conta total e "em comum" em memória. */
    @Query("select c.concurso.id, c.assunto.id from CoberturaEdital c")
    List<Object[]> listarPares();

    @Modifying
    @Query("delete from CoberturaEdital c where c.concurso.id = :concursoId")
    void removerDoConcurso(Long concursoId);
}
