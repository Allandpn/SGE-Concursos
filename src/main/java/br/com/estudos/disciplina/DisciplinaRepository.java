package br.com.estudos.disciplina;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DisciplinaRepository extends JpaRepository<Disciplina,Long> {
    List<Disciplina> findByAtivoTrue();

    // ux_d47_nome_disciplina garante a unicidade (D-47); este método é usado
    // só para resolver a importação por nome, não como verificação prévia.
    Optional<Disciplina> findByNomeIgnoreCase(String nome);
}
