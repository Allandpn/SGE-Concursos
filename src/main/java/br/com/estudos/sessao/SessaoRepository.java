package br.com.estudos.sessao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessaoRepository extends JpaRepository<Sessao, Long> {

    // D-45 (docs/SPRINT-3-SESSAO.md §3.3): reenvio da mesma tentativa busca aqui,
    // nunca cria de novo — a unicidade de ux_sessao_d45_tentativa_unica garante
    // que existe no máximo uma.
    Optional<Sessao> findByTentativaId(UUID tentativaId);

    List<Sessao> findByAssuntoId(Long assuntoId);
}
