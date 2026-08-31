package br.com.estudos.sessao;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.estudos.metrica.AcertoLinha;
import br.com.estudos.shared.enums.TipoSessao;

public interface SessaoRepository extends JpaRepository<Sessao, Long> {

    // D-45 (docs/SPRINT-3-SESSAO.md §3.3): reenvio da mesma tentativa busca aqui,
    // nunca cria de novo — a unicidade de ux_sessao_d45_tentativa_unica garante
    // que existe no máximo uma.
    Optional<Sessao> findByTentativaId(UUID tentativaId);

    List<Sessao> findByAssuntoId(Long assuntoId);

    // D-24 (docs/SPRINT-5-FRENTE.md §2.1): assunto sem nenhuma sessão está no backlog.
    boolean existsByAssuntoId(Long assuntoId);

    // M-2 (docs/SPRINT-7-METRICAS.md §4.2): primeira exposição = data mais
    // antiga; ordenado, o service só pega head/tail da lista, por tipo.
    // Desempate por id: duas sessões do mesmo tipo no mesmo dia (ex.: duas
    // QUESTOES no mesmo dia) deixariam a ordem por data sozinha indefinida —
    // o Postgres não garante ordem estável entre linhas empatadas na chave
    // de sort, e a "primeira exposição" mudaria de uma chamada pra outra.
    List<Sessao> findByAssuntoIdAndTipoInOrderByDataAscIdAsc(Long assuntoId, Collection<TipoSessao> tipos);

    // M-1 (docs/SPRINT-7-METRICAS.md §4.1): projeção, não entidade — evita
    // N+1 de Assunto/Disciplina LAZY (mesmo cuidado do "suspeito de sempre").
    @Query("""
        select a.disciplina.id as disciplinaId, s.formato as formato,
               s.questoesCorretas as questoesCorretas, s.questoesTotal as questoesTotal
        from Sessao s
        join s.assunto a
        where s.tipo = br.com.estudos.shared.enums.TipoSessao.QUESTOES
          and s.data between :inicio and :fim
        """)
    List<AcertoLinha> listarAcertoQuestoes(@Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    // M-3 objetiva (docs/SPRINT-7-METRICAS.md §4.3): QUESTOES e FLASHCARDS
    // compartilham previsaoPercentual — nenhuma regra (D-12 é só sobre M-1)
    // exclui FLASHCARDS aqui.
    List<Sessao> findByTipoInAndPrevisaoPercentualNotNull(Collection<TipoSessao> tipos);

    // M-3 subjetiva (docs/SPRINT-7-METRICAS.md §4.3).
    List<Sessao> findByTipoAndPrevisaoReconstrucaoNotNull(TipoSessao tipo);
}
