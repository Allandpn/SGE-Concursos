package br.com.estudos.assunto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssuntoRepository extends JpaRepository<Assunto, Long> {
    List<Assunto> findByDisciplinaIdAndAtivoTrue(Long disciplinaId);
    Optional<Assunto> findByDisciplinaIdAndNomeIgnoreCase(Long disciplinaId, String nome);

    // join fetch: exportação lê o nome da disciplina de todo assunto ativo,
    // sem isto seria N+1 (09_CODE_STYLE checklist).
    @Query("""
        select a from Assunto a
        join fetch a.disciplina d
        where a.ativo = true
        order by d.nome, a.ordem
        """)
    List<Assunto> listarParaExportacao();

    // Base da frente/backlog (docs/SPRINT-5-FRENTE.md §2) — join fetch pra
    // FrenteService ler peso/disciplina.ativo sem N+1.
    @Query("""
        select a from Assunto a
        join fetch a.disciplina d
        where a.ativo = true and d.ativo = true
        """)
    List<Assunto> listarAtivosDeDisciplinasAtivas();
}
