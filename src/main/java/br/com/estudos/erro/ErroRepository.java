package br.com.estudos.erro;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErroRepository extends JpaRepository<Erro, Long> {

    // ADR-034 (open-in-view: false): ErroMapper lê assunto.id e sessao.id —
    // sem join fetch seria N+1 (09_CODE_STYLE checklist). sessao é opcional
    // (D-46), por isso left join.
    @Query("""
        select e from Erro e
        join fetch e.assunto a
        left join fetch e.sessao s
        where e.assunto.id = :assuntoId
        """)
    List<Erro> findByAssuntoId(@Param("assuntoId") Long assuntoId);
}
