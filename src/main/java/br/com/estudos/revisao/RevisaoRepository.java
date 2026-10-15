package br.com.estudos.revisao;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.SituacaoRevisao;

public interface RevisaoRepository extends JpaRepository<Revisao, Long> {

    // D-05: no máximo uma pendente por assunto — Optional, não List.
    Optional<Revisao> findByAssuntoIdAndSituacao(Long assuntoId, SituacaoRevisao situacao);

    List<Revisao> findByAssuntoId(Long assuntoId);

    // D-49: a última revisão do assunto, qualquer situação — é dela que
    // reativar lê o nível a herdar (join fetch: precisa do peso do assunto
    // e do assunto_id da sessaoOrigem antes de decidir manutenção ou não).
    @Query("""
        select r from Revisao r
        join fetch r.assunto a
        where r.assunto.id = :assuntoId
        order by r.id desc
        limit 1
        """)
    Optional<Revisao> buscarUltimaPorAssunto(@Param("assuntoId") Long assuntoId);

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

    // D-10/D-11 (01_DOMINIO §5.5): resultados cumpridos no nível-alvo, da
    // mais antiga à mais nova — EstadoConsolidacao percorre a sequência.
    // Ordenado por id porque a revisão nasce PENDENTE e vira CUMPRIDA na
    // mesma linha, então a ordem de criação já é a ordem de progressão.
    @Query("""
        select s.resultado from Revisao r
        join r.sessaoCumpriu s
        where r.assunto.id = :assuntoId
          and r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.CUMPRIDA
          and r.nivel = :nivel
        order by r.id asc
        """)
    List<ResultadoSessao> listarResultadosCumpridos(@Param("assuntoId") Long assuntoId, @Param("nivel") Integer nivel);

    // Fase em lote (docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §3): as cumpridas
    // de um conjunto de assuntos numa consulta só, em vez de uma por assunto.
    @Query("""
        select r.assunto.id as assuntoId, r.nivel as nivel, s.resultado as resultado, s.data as data
        from Revisao r
        join r.sessaoCumpriu s
        where r.assunto.id in :assuntoIds
          and r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.CUMPRIDA
        order by r.id asc
        """)
    List<ResultadoCumpridoLinha> listarCumpridasDosAssuntos(@Param("assuntoIds") Collection<Long> assuntoIds);

    // Reestudo (D-07/D-11, 01_DOMINIO §5.1 v1.19.0): pendente nascida de uma
    // FALHA sem ESTUDO registrado depois dela. "Depois" é ordem de registro
    // (id), não de data — docs/SPRINT-16-CORRECOES-MULTIEDITAL.md §2.1.
    @Query("""
        select r from Revisao r
        join fetch r.assunto a
        join fetch r.sessaoOrigem o
        where r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.PENDENTE
          and o.resultado = br.com.estudos.shared.enums.ResultadoSessao.FALHA
          and not exists (
              select 1 from Sessao e
              where e.assunto = a
                and e.tipo = br.com.estudos.shared.enums.TipoSessao.ESTUDO
                and e.id > o.id)
        order by o.data asc, o.id asc
        """)
    List<Revisao> listarEmReestudo();

    // "Quer adiantar?" (02_JORNADAS §4.3, docs/SPRINT-17-USO-REAL.md §6.4):
    // pendentes ainda não vencidas, a mais próxima primeiro — quem decide se
    // a janela de tolerância já abriu é o RevisaoService (§5.4).
    @Query("""
        select r from Revisao r
        join fetch r.assunto a
        join fetch r.sessaoOrigem o
        where r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.PENDENTE
          and r.dataPrevista > :hoje
        order by r.dataPrevista asc, r.id asc
        """)
    List<Revisao> listarFuturas(@Param("hoje") LocalDate hoje);

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

    // M-4 (docs/SPRINT-7-METRICAS.md §4.4): join fetch em sessaoCumpriu — é
    // dela que vem a data real de conclusão, pra comparar com dataPrevista.
    @Query("""
        select r from Revisao r
        join fetch r.sessaoCumpriu s
        where r.situacao = br.com.estudos.shared.enums.SituacaoRevisao.CUMPRIDA
        """)
    List<Revisao> listarCumpridasComSessao();
}
