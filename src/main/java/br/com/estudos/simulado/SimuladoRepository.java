package br.com.estudos.simulado;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SimuladoRepository extends JpaRepository<Simulado, Long> {

    // Histórico (docs/SPRINT-8-SIMULADO.md §5): mais recente primeiro.
    // findAll() sem ORDER BY é ordem indefinida — mesmo cuidado do desempate
    // de M-2 (docs/SPRINT-7-METRICAS.md, achado na revisão de código).
    List<Simulado> findAllByOrderByDataDesc();
}
