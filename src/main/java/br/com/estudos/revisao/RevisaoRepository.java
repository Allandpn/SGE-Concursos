package br.com.estudos.revisao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.estudos.shared.enums.SituacaoRevisao;

public interface RevisaoRepository extends JpaRepository<Revisao, Long> {

    // D-05: no máximo uma pendente por assunto — Optional, não List.
    Optional<Revisao> findByAssuntoIdAndSituacao(Long assuntoId, SituacaoRevisao situacao);

    List<Revisao> findByAssuntoId(Long assuntoId);

    // Represamento (docs/SPRINT-5-FRENTE.md §2.2): pendentes cuja data já passou.
    long countBySituacaoAndDataPrevistaBefore(SituacaoRevisao situacao, LocalDate data);

    // Fila de recuperação do turno (docs/SPRINT-6-TURNO.md §2): mais atrasada
    // primeiro. join fetch: o plano mostra o nome do assunto sem N+1.
    @Query("""
        select r from Revisao r
        join fetch r.assunto a
        where r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.PENDENTE
          and r.dataPrevista <= :hoje
        order by r.dataPrevista asc
        """)
    List<Revisao> listarVencidasPorAtraso(@Param("hoje") LocalDate hoje);

    // "Os dois últimos resultados [no nível-alvo]" (01_DOMINIO §6.4/D-10) —
    // filtra por nível: "escada concluída" é precondição, e as duas tentativas
    // contadas para consolidar têm que ser NO nível-alvo, não em qualquer
    // degrau anterior que tenha rendido SUCESSO no caminho até ali. Ordenado
    // por id porque a revisão nasce PENDENTE e vira CUMPRIDA na mesma linha,
    // então a ordem de criação já é a ordem de progressão na escada.
    List<Revisao> findTop2ByAssuntoIdAndSituacaoAndNivelOrderByIdDesc(
        Long assuntoId, SituacaoRevisao situacao, Integer nivel);

    // D-17 (docs/SPRINT-4-ESCADA.md §3.4): cancela sem tocar em CUMPRIDA (D-18).
    // flushAutomatically: AssuntoService/DisciplinaService.arquivar chamam isto
    // depois de um setAtivo(false) que ainda não foi pro banco (dirty checking
    // só flusharia no commit) — sem isto, clearAutomatically abaixo descartaria
    // essa alteração pendente antes dela ser escrita.
    // clearAutomatically: update em massa não passa pelo contexto de persistência
    // — sem isto, uma Revisao já carregada nesta mesma transação (ex.: buscada
    // antes de arquivar) continua com o valor velho em memória, e um findById
    // logo depois devolveria PENDENTE mesmo já CANCELADA no banco.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Revisao r set r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.CANCELADA "
        + "where r.assunto.id = :assuntoId and r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.PENDENTE")
    void cancelarPendentePorAssunto(@Param("assuntoId") Long assuntoId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Revisao r set r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.CANCELADA "
        + "where r.assunto.disciplina.id = :disciplinaId and r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.PENDENTE")
    void cancelarPendentesPorDisciplina(@Param("disciplinaId") Long disciplinaId);
}
