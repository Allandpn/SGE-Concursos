package br.com.estudos.disciplina;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisciplinaRepository extends JpaRepository<Disciplina,Long> {
    List<Disciplina> findByAtivoTrue();

    // Sem restrição de unicidade no banco para nome de disciplina (não há
    // ux_disciplina_nome) — usado só para resolver a importação por nome,
    // não como garantia de unicidade.
    Optional<Disciplina> findByNomeIgnoreCase(String nome);
}
