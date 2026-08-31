package br.com.estudos.erro;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ErroRepository extends JpaRepository<Erro, Long> {

    List<Erro> findByAssuntoId(Long assuntoId);
}
