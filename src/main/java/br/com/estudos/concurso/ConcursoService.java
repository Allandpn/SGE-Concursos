package br.com.estudos.concurso;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.segmento.MaterialService;
import br.com.estudos.shared.enums.OrigemConcurso;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;

/**
 * Concurso-alvo e histórico (01_DOMINIO §3.8, ADR-042,
 * docs/SPRINT-18-CONCURSOS.md §3). A única porta para mudar o concurso-alvo:
 * {@link #ativarComCobertura} — usada pela confirmação da análise de edital,
 * pela importação do planejamento e pela reativação.
 */
@Service
public class ConcursoService {

    private final ConcursoRepository concursoRepository;
    private final CoberturaEditalRepository coberturaRepository;
    private final AssuntoRepository assuntoRepository;
    private final MaterialService materialService;
    private final Clock clock;

    public ConcursoService(
            ConcursoRepository concursoRepository,
            CoberturaEditalRepository coberturaRepository,
            AssuntoRepository assuntoRepository,
            MaterialService materialService,
            Clock clock) {
        this.concursoRepository = concursoRepository;
        this.coberturaRepository = coberturaRepository;
        this.assuntoRepository = assuntoRepository;
        this.materialService = materialService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<Concurso> ativo() {
        return concursoRepository.findByAtivoTrue();
    }

    /** Dias até a prova pela data de hoje do servidor — derivado, nunca guardado (princípio 1). */
    public Long diasParaProva(Concurso concurso) {
        if (concurso.getDataProva() == null) return null;
        return ChronoUnit.DAYS.between(LocalDate.now(clock), concurso.getDataProva());
    }

    /** Histórico: o ativo primeiro, depois do mais recente ao mais antigo, com as contagens da cobertura. */
    @Transactional(readOnly = true)
    public List<ConcursoComContagem> historico() {
        var assuntosPorConcurso = new HashMap<Long, Set<Long>>();
        for (var par : coberturaRepository.listarPares()) {
            assuntosPorConcurso.computeIfAbsent((Long) par[0], k -> new HashSet<>()).add((Long) par[1]);
        }
        var concursos = new ArrayList<>(concursoRepository.findAllByOrderByAtivadoEmDescIdDesc());
        concursos.sort(Comparator.comparing((Concurso c) -> !c.isAtivo())); // estável: mantém o resto
        var doAtivo = concursos.stream().filter(Concurso::isAtivo).findFirst()
            .map(c -> assuntosPorConcurso.getOrDefault(c.getId(), Set.of()))
            .orElse(Set.of());
        return concursos.stream().map(c -> {
            var deste = assuntosPorConcurso.getOrDefault(c.getId(), Set.of());
            long emComum = deste.stream().filter(doAtivo::contains).count();
            return new ConcursoComContagem(c, deste.size(), emComum, deste);
        }).toList();
    }

    public record ConcursoComContagem(Concurso concurso, int totalAssuntos, long emComumComAtivo, Set<Long> assuntoIds) {}

    /** Resposta de um concurso com as contagens do histórico e os dias até a prova. */
    @Transactional(readOnly = true)
    public ConcursoResponse resposta(Long id) {
        return historico().stream()
            .filter(c -> c.concurso().getId().equals(id))
            .findFirst()
            .map(this::resposta)
            .orElseThrow(() -> new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE"));
    }

    public ConcursoResponse resposta(ConcursoComContagem c) {
        var concurso = c.concurso();
        return new ConcursoResponse(
            concurso.getId(), concurso.getNome(), concurso.getCargo(), concurso.getBanca(), concurso.getDataProva(),
            concurso.getOrigem(), concurso.isAtivo(), concurso.getAtivadoEm(), concurso.getDesativadoEm(),
            diasParaProva(concurso), c.totalAssuntos(), c.emComumComAtivo(),
            // Só no alvo: contar o material de todo o histórico a cada tela não serve a ninguém.
            concurso.isAtivo() ? materialService.resumo(c.assuntoIds()) : null);
    }

    /**
     * Cobertura do concurso na forma que o planejamento lê (docs/SPRINT-18-CONCURSOS.md §2).
     * Só leitura: não ativa nem aplica nada. Assunto arquivado depois do edital fica de fora.
     */
    @Transactional(readOnly = true)
    public PlanejamentoConcursoResponse paraPlanejamento(Long id) {
        var concurso = concursoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE"));
        var cobertura = coberturaRepository.listarDoConcurso(id).stream()
            .filter(c -> c.getAssunto().isAtivo())
            .toList();
        var estados = materialService.estados(cobertura.stream().map(c -> c.getAssunto().getId()).toList());
        var assuntos = cobertura.stream()
            .map(c -> {
                var estado = estados.get(c.getAssunto().getId());
                return new PlanejamentoConcursoResponse.Assunto(
                    c.getDisciplina().getNome(), c.getAssunto().getNome(), c.getPeso(), c.getOrdem(),
                    c.getAssunto().getChaveExterna(), estado.segmentos(), estado.lacuna());
            })
            .toList();
        return new PlanejamentoConcursoResponse(
            concurso.getSlug(), concurso.getNome(), concurso.getCargo(), concurso.getBanca(),
            concurso.getDataProva(), assuntos);
    }

    /** Reativa um concurso do histórico aplicando de novo a cobertura dele (D-59, D-60). */
    @Transactional
    public Concurso reativar(Long id) {
        var concurso = concursoRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Concurso não encontrado.", "CONCURSO_INEXISTENTE"));
        if (concurso.isAtivo()) return concurso;
        var itens = coberturaRepository.listarDoConcurso(id).stream()
            // Assunto arquivado depois que o edital foi aplicado fica arquivado:
            // a cobertura aplica peso e ordem, não desfaz decisão do usuário.
            .filter(c -> c.getAssunto().isAtivo())
            .map(c -> new ItemCobertura(c.getAssunto(), c.getDisciplina(), c.getPeso(), c.getOrdem()))
            .toList();
        aplicarCobertura(itens);
        return ativar(concurso);
    }

    /**
     * Concurso novo ou atualizado com a cobertura dada: aplica e ativa.
     * A cobertura gravada é substituída pela nova — é o retrato do edital
     * atual daquele concurso, não estado de estudo (ADR-011 trata de registro
     * do usuário; nenhum assunto, sessão ou revisão é apagado aqui).
     */
    @Transactional
    public Concurso ativarComCobertura(Concurso concurso, List<ItemCobertura> itens) {
        aplicarCobertura(itens);
        var salvo = ativar(concurso);
        coberturaRepository.removerDoConcurso(salvo.getId());
        coberturaRepository.flush();
        coberturaRepository.saveAll(itens.stream()
            .map(i -> new CoberturaEdital(salvo, i.assunto(), i.disciplina(), i.peso(), i.ordem()))
            .toList());
        return salvo;
    }

    /**
     * D-64 — identificador de um concurso que está nascendo: o nome sem acento,
     * em minúsculas, com hífen; já usado, ganha "-2", "-3"... Dado uma vez e
     * nunca mudado depois, mesmo que o nome seja corrigido
     * (docs/SPRINT-19-MATERIAL-AUTOMATICO.md §2).
     */
    @Transactional(readOnly = true)
    public String novoSlug(String nome) {
        var semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        var base = semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        if (base.isEmpty()) base = "concurso";
        if (base.length() > 140) base = base.substring(0, 140).replaceAll("-+$", "");
        var slug = base;
        for (int n = 2; concursoRepository.existsBySlug(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    /** Concurso vindo do planejamento: reencontrado pelo slug da pasta, ou criado (docs/SPRINT-18-CONCURSOS.md §5). */
    @Transactional
    public Concurso registrarDoPlanejamento(String slug, String nome, LocalDate dataProva, List<ItemCobertura> itens) {
        var concurso = concursoRepository.findBySlug(slug)
            .orElseGet(() -> new Concurso(nome, OrigemConcurso.PLANEJAMENTO, slug));
        concurso.setNome(nome);
        concurso.setDataProva(dataProva);
        return ativarComCobertura(concurso, itens);
    }

    /**
     * D-59: encerra o período do concurso ativo e ativa este. Desativa e
     * flusha ANTES de ativar — ux_concurso_d58_um_ativo (D-58) recusaria dois
     * ativos no meio do caminho.
     */
    private Concurso ativar(Concurso concurso) {
        var agora = Instant.now(clock);
        concursoRepository.findByAtivoTrue()
            .filter(atual -> !atual.equals(concurso))
            .ifPresent(atual -> {
                atual.setAtivo(false);
                atual.setDesativadoEm(agora);
                concursoRepository.saveAndFlush(atual);
            });
        concurso.setAtivo(true);
        concurso.setAtivadoEm(agora);
        concurso.setDesativadoEm(null);
        try {
            return concursoRepository.saveAndFlush(concurso);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Outro concurso foi ativado ao mesmo tempo. Tente de novo.", "CONCURSO_ATIVO_CONCORRENTE");
        }
    }

    /**
     * D-60 — aplica peso, disciplina e ordem da cobertura. Nos assuntos
     * cobertos, a ordem do edital; nos ativos das mesmas disciplinas que o
     * edital não cita, a ordem continua depois dos cobertos, na sequência em
     * que já estavam. Escada, sessões, revisões e erros não aparecem aqui —
     * é isso que D-60 promete.
     *
     * D-48 (ordem única entre ativos da disciplina) vale a cada escrita, então
     * todo assunto que muda de ordem ou de disciplina passa antes por uma
     * ordem provisória única (−id), a mesma técnica da importação (Sprint 16).
     */
    @Transactional
    public void aplicarCobertura(List<ItemCobertura> itens) {
        var destinos = new LinkedHashMap<Assunto, Destino>();
        var maiorOrdemPorDisciplina = new LinkedHashMap<Long, Integer>();
        var disciplinasPorId = new HashMap<Long, Disciplina>();
        for (var item : itens) {
            destinos.put(item.assunto(), new Destino(item.disciplina(), item.peso(), item.ordem()));
            maiorOrdemPorDisciplina.merge(item.disciplina().getId(), item.ordem(), Math::max);
            disciplinasPorId.put(item.disciplina().getId(), item.disciplina());
        }
        var cobertos = new LinkedHashSet<>(destinos.keySet());
        for (var entrada : maiorOrdemPorDisciplina.entrySet()) {
            var disciplina = disciplinasPorId.get(entrada.getKey());
            int proxima = entrada.getValue();
            var foraDoEdital = assuntoRepository.findByDisciplinaIdAndAtivoTrue(entrada.getKey()).stream()
                .filter(a -> !cobertos.contains(a))
                .sorted(Comparator.comparing(Assunto::getOrdem, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Assunto::getId))
                .toList();
            for (var assunto : foraDoEdital) {
                destinos.put(assunto, new Destino(disciplina, assunto.getPeso(), ++proxima));
            }
        }

        var mudando = new ArrayList<Assunto>();
        destinos.forEach((assunto, destino) -> {
            boolean mudaOrdem = !Integer.valueOf(destino.ordem()).equals(assunto.getOrdem());
            boolean mudaDisciplina = assunto.getDisciplina() == null
                || !assunto.getDisciplina().getId().equals(destino.disciplina().getId());
            if (mudaOrdem || mudaDisciplina) {
                assunto.setOrdem(Math.toIntExact(-assunto.getId()));
                mudando.add(assunto);
            }
        });
        try {
            if (!mudando.isEmpty()) assuntoRepository.saveAllAndFlush(mudando);
            destinos.forEach((assunto, destino) -> {
                assunto.setDisciplina(destino.disciplina());
                assunto.setPeso(destino.peso());
                assunto.setOrdem(destino.ordem());
            });
            assuntoRepository.saveAllAndFlush(destinos.keySet());
        } catch (DataIntegrityViolationException e) {
            var causa = e.getMostSpecificCause().getMessage();
            if (causa != null && causa.contains("ux_assunto_j1_nome_por_disciplina")) {
                throw new ConflictException(
                    "Dois assuntos com o mesmo nome acabariam na mesma disciplina. Desfaça a correspondência de um deles.",
                    "NOME_DUPLICADO");
            }
            throw e;
        }
    }

    private record Destino(Disciplina disciplina, TipoPeso peso, int ordem) {}
}
