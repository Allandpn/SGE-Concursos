package br.com.estudos.edital;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.assunto.AssuntoRequest;
import br.com.estudos.assunto.AssuntoService;
import br.com.estudos.concurso.Concurso;
import br.com.estudos.concurso.ConcursoService;
import br.com.estudos.concurso.ItemCobertura;
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.disciplina.DisciplinaRepository;
import br.com.estudos.disciplina.DisciplinaRequest;
import br.com.estudos.disciplina.DisciplinaService;
import br.com.estudos.shared.enums.OrigemConcurso;
import br.com.estudos.shared.enums.StatusAnaliseEdital;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Concluir a conferência do edital (01_DOMINIO §3.12, D-62, D-92; docs/SPRINT-25-CONFERENCIA-EDITAL.md §7):
 * o momento em que disciplinas, assuntos e concurso passam a existir.
 */
@Service
public class ConclusaoEditalService {

    private static final int MAX_NOME_ASSUNTO = 250;
    private static final int MAX_NOME_DISCIPLINA = 150;

    private final AnaliseEditalRepository repository;
    private final AssuntoRepository assuntoRepository;
    private final AssuntoService assuntoService;
    private final DisciplinaRepository disciplinaRepository;
    private final DisciplinaService disciplinaService;
    private final ConcursoService concursoService;

    public ConclusaoEditalService(
            AnaliseEditalRepository repository,
            AssuntoRepository assuntoRepository,
            AssuntoService assuntoService,
            DisciplinaRepository disciplinaRepository,
            DisciplinaService disciplinaService,
            ConcursoService concursoService) {
        this.repository = repository;
        this.assuntoRepository = assuntoRepository;
        this.assuntoService = assuntoService;
        this.disciplinaRepository = disciplinaRepository;
        this.disciplinaService = disciplinaService;
        this.concursoService = concursoService;
    }

    /** O que a conferência concluída pede para cadastrar; nada aqui é confiado (D-63 revalida o que for correspondência). */
    public record PlanoConclusao(String nome, String cargo, String banca, java.time.LocalDate dataProva, List<Disciplina> disciplinas) {
        public record Disciplina(String nome, Long disciplinaExistenteId, TipoPeso peso, List<Assunto> assuntos) {}

        public record Assunto(String nome, Long assuntoExistenteId) {}
    }

    /** O concurso criado e os assuntos cadastrados, na ordem em que o plano os listou (disciplina por disciplina). */
    public record Resultado(Concurso concurso, List<Assunto> assuntos) {}

    // ---------------------------------------------------------------- conclusão

    /**
     * D-62, D-92 — a única escrita no cadastro. Cria o que é novo, aplica a
     * cobertura (D-60) e ativa o concurso. Tudo numa transação: falhou, nada
     * fica. D-64: o concurso nasce com identificador e todo assunto coberto
     * sem chave externa ganha uma — o material preparado depois reencontra os
     * dois por elas, nunca pelo nome (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §2).
     * Devolve também, por assunto do plano, o assunto cadastrado (para a
     * conferência guardar a ligação).
     */
    @Transactional
    public Resultado concluir(AnaliseEdital analise, PlanoConclusao pedido) {
        var nomeConcurso = AnaliseEditalService.cortar(pedido.nome(), 250);
        if (nomeConcurso == null) {
            throw new ValidationException("Informe o nome do concurso.", "NOME_OBRIGATORIO", "nome");
        }
        var ativos = assuntoRepository.listarAtivosDeDisciplinasAtivas().stream()
            .collect(Collectors.toMap(Assunto::getId, Function.identity()));
        var todasDisciplinas = new ArrayList<>(disciplinaRepository.findAll());
        var usados = new HashSet<Long>();
        var itens = new ArrayList<ItemCobertura>();
        var assuntosPorPosicao = new ArrayList<Assunto>();
        int provisoria = 0;

        for (var pedidoDisciplina : pedido.disciplinas() == null ? List.<PlanoConclusao.Disciplina>of() : pedido.disciplinas()) {
            var nomesAssunto = pedidoDisciplina.assuntos() == null ? List.<PlanoConclusao.Assunto>of()
                : pedidoDisciplina.assuntos().stream().filter(a -> AnaliseEditalService.cortar(a.nome(), MAX_NOME_ASSUNTO) != null).toList();
            var nomeDisciplina = AnaliseEditalService.cortar(pedidoDisciplina.nome(), MAX_NOME_DISCIPLINA);
            if (nomesAssunto.isEmpty() || nomeDisciplina == null) continue;
            var peso = pedidoDisciplina.peso() == null ? TipoPeso.MEDIO : pedidoDisciplina.peso();
            var disciplina = resolverDisciplina(pedidoDisciplina.disciplinaExistenteId(), nomeDisciplina, peso, todasDisciplinas);
            disciplina.setPeso(peso);

            int ordem = 0;
            for (var pedidoAssunto : nomesAssunto) {
                var nome = AnaliseEditalService.cortar(pedidoAssunto.nome(), MAX_NOME_ASSUNTO);
                // D-63: a correspondência do corpo é revalidada aqui.
                var assunto = pedidoAssunto.assuntoExistenteId() == null ? null : ativos.get(pedidoAssunto.assuntoExistenteId());
                if (assunto != null && !usados.add(assunto.getId())) assunto = null;
                if (assunto == null) assunto = homonimo(disciplina, nome, usados);
                if (assunto == null) {
                    assunto = assuntoService.criar(new AssuntoRequest(
                        disciplina.getId(), nome, peso, (short) 3, -1_000_000 - (++provisoria), null));
                    usados.add(assunto.getId());
                }
                itens.add(new ItemCobertura(assunto, disciplina, peso, ++ordem));
                assuntosPorPosicao.add(assunto);
            }
        }
        if (itens.isEmpty()) {
            throw new ValidationException("A proposta ficou sem nenhum assunto.", "PROPOSTA_VAZIA", "disciplinas");
        }

        for (var item : itens) {
            if (item.assunto().getChaveExterna() == null) {
                item.assunto().setChaveExterna(UUID.randomUUID().toString()); // D-64
            }
        }

        var concurso = new Concurso(nomeConcurso, OrigemConcurso.ANALISE_IA, concursoService.novoSlug(nomeConcurso));
        concurso.setCargo(AnaliseEditalService.cortar(pedido.cargo(), 250));
        concurso.setBanca(AnaliseEditalService.cortar(pedido.banca(), 150));
        concurso.setDataProva(pedido.dataProva());
        var salvo = concursoService.ativarComCobertura(concurso, itens);

        analise.setStatus(StatusAnaliseEdital.CONFIRMADA);
        analise.setConcursoId(salvo.getId());
        repository.save(analise);
        return new Resultado(salvo, assuntosPorPosicao);
    }

    /** Disciplina pelo id (ativa), senão pelo nome sem acento e sem caixa (D-47: único mesmo arquivada), senão nova. */
    private Disciplina resolverDisciplina(Long idExistente, String nome, TipoPeso peso, List<Disciplina> todas) {
        if (idExistente != null) {
            var porId = todas.stream().filter(d -> d.getId().equals(idExistente) && d.isAtivo()).findFirst();
            if (porId.isPresent()) return porId.get();
        }
        var alvo = AnaliseEditalService.normalizar(nome);
        var porNome = todas.stream().filter(d -> AnaliseEditalService.normalizar(d.getNome()).equals(alvo)).findFirst();
        if (porNome.isPresent()) {
            if (!porNome.get().isAtivo()) disciplinaService.reativar(porNome.get().getId());
            return porNome.get();
        }
        var nova = disciplinaService.criar(new DisciplinaRequest(nome, peso));
        todas.add(nova);
        return nova;
    }

    /**
     * Assunto da disciplina com o mesmo nome (sem acento, sem caixa) — o
     * índice de nome valeria contra criar outro. Arquivado volta a ativo: o
     * edital novo o cobre de novo; antes, ordem provisória, porque a antiga
     * pode ter sido ocupada enquanto ele estava arquivado (D-48).
     */
    private Assunto homonimo(Disciplina disciplina, String nome, Set<Long> usados) {
        if (disciplina.getId() == null) return null;
        var alvo = AnaliseEditalService.normalizar(nome);
        var achado = assuntoRepository.findByDisciplinaId(disciplina.getId()).stream()
            .filter(a -> AnaliseEditalService.normalizar(a.getNome()).equals(alvo) && !usados.contains(a.getId()))
            .findFirst()
            .orElse(null);
        if (achado == null) return null;
        if (!achado.isAtivo()) {
            achado.setOrdem(Math.toIntExact(-achado.getId()));
            assuntoRepository.saveAndFlush(achado);
            assuntoService.reativar(achado.getId());
        }
        usados.add(achado.getId());
        return achado;
    }
}
