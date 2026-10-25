package br.com.estudos.conferencia;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import br.com.estudos.arvore.ArvoreResponses.No;
import br.com.estudos.arvore.ArvoreService;
import br.com.estudos.conferencia.ConferenciaDados.Ass;
import br.com.estudos.conferencia.ConferenciaDados.Disc;
import br.com.estudos.conferencia.ConferenciaPedidos.Dividir;
import br.com.estudos.conferencia.ConferenciaPedidos.Juntar;
import br.com.estudos.conferencia.ConferenciaPedidos.Nos;
import br.com.estudos.conferencia.ConferenciaPedidos.ResolverTrecho;
import br.com.estudos.conferencia.ConferenciaPedidos.TextoDaParte;
import br.com.estudos.conferencia.ConferenciaResponses.Assunto;
import br.com.estudos.conferencia.ConferenciaResponses.Disciplina;
import br.com.estudos.conferencia.ConferenciaResponses.Execucao;
import br.com.estudos.conferencia.ConferenciaResponses.ItemDaFila;
import br.com.estudos.conferencia.ConferenciaResponses.Parte;
import br.com.estudos.conferencia.ConferenciaResponses.Pendencias;
import br.com.estudos.conferencia.ConferenciaResponses.Resumo;
import br.com.estudos.conferencia.RegrasEdital.AssuntoRegra;
import br.com.estudos.conferencia.RegrasEdital.ParteRegra;
import br.com.estudos.edital.AnaliseEdital;
import br.com.estudos.edital.AnaliseEditalRepository;
import br.com.estudos.edital.PesoPorQuestoes;
import br.com.estudos.shared.enums.StatusAnaliseEdital;
import br.com.estudos.shared.enums.TipoPeso;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * A conferência do edital (01_DOMINIO §3.12, D-86 a D-92; docs/SPRINT-25-CONFERENCIA-EDITAL.md §5 e §6). O resumo, a
 * árvore do edital e a fila saem do que está guardado (D-16); cada gesto da pessoa grava um evento e, quando muda a
 * ligação, uma decisão presa à parte e ao contexto (D-86, D-87).
 */
@Service
public class ConferenciaService {

    private static final Set<String> NATUREZAS = Set.of("EXATO", "MAIS_AMPLO", "PARCIAL");

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final AnaliseEditalRepository analises;
    private final ArvoreService arvore;

    public ConferenciaService(JdbcTemplate jdbc, JsonMapper json, AnaliseEditalRepository analises, ArvoreService arvore) {
        this.jdbc = jdbc;
        this.json = json;
        this.analises = analises;
        this.arvore = arvore;
    }

    // ================================================================ leitura

    @Transactional(readOnly = true)
    public Resumo resumo(Long analiseId) {
        var analise = conferencia(analiseId);
        var dados = ConferenciaDados.ler(jdbc, analiseId);
        var fila = fila(dados);
        var ativas = dados.partes.stream().filter(ConferenciaDados.Parte::ativa).toList();
        var porNatureza = new TreeMap<String, Integer>();
        for (var n : NATUREZAS) porNatureza.put(n, 0);
        int semNo = 0;
        for (var p : ativas) {
            if (p.nos().isEmpty()) semNo++;
            else if (p.natureza() != null) porNatureza.merge(p.natureza(), 1, Integer::sum);
        }
        int bloqueiam = (int) fila.stream().filter(ItemDaFila::bloqueia).count();
        var execucao = jdbc.query("""
            SELECT e.modelo, e.executada_em, v.rotulo FROM edital_execucao e JOIN arvore_versao v ON v.id = e.arvore_versao_id
             WHERE e.analise_id = ? ORDER BY e.id DESC LIMIT 1""",
            (rs, i) -> new Execucao(rs.getString("modelo"), rs.getString("executada_em"), rs.getString("rotulo")), analiseId)
            .stream().findFirst().orElse(null);
        int porPessoa = (int) ativas.stream().filter(p -> List.of("ACEITA", "DECIDIDA").contains(p.estado()) && !"DECISAO".equals(p.origem())).count();
        int anteriores = (int) ativas.stream().filter(p -> "DECISAO".equals(p.origem())).count();
        return new Resumo(analiseId, analise.getStatus().name(), analise.getCargoEscolhido(), analise.getConcursoNome(), execucao,
            dados.disciplinas.size(), (int) dados.assuntos.stream().filter(Ass::aparece).count(), ativas.size(), porNatureza, semNo,
            new Pendencias(bloqueiam, fila.size() - bloqueiam), porPessoa, anteriores,
            (int) ativas.stream().filter(ConferenciaDados.Parte::iaMudou).count(), bloqueiam == 0);
    }

    @Transactional(readOnly = true)
    public List<Disciplina> disciplinas(Long analiseId) {
        conferencia(analiseId);
        return montar(ConferenciaDados.ler(jdbc, analiseId));
    }

    @Transactional(readOnly = true)
    public List<ItemDaFila> fila(Long analiseId) {
        conferencia(analiseId);
        return fila(ConferenciaDados.ler(jdbc, analiseId));
    }

    /** A fila de revisão, derivada (docs §6): bloqueantes primeiro, depois a gravidade e a ordem do edital. */
    List<ItemDaFila> fila(ConferenciaDados d) {
        var itens = new ArrayList<ItemDaFila>();
        for (var t : d.trechos) {
            if (!"ABERTO".equals(t.estado())) continue;
            var disc = t.disciplinaId() == null ? null : d.disciplinaPorId.get(t.disciplinaId());
            itens.add(new ItemDaFila(t.tipo(), 1, true, disc == null ? null : disc.id(), disc == null ? null : disc.nome(), null, null,
                null, null, t.id(), ConferenciaDados.lista(json, t.linhas())));
        }
        for (var a : d.assuntos) {
            if (!a.aparece()) continue;
            var disc = d.disciplinaPorId.get(a.disciplinaId());
            var erros = ConferenciaDados.lista(json, a.erros());
            var alertas = ConferenciaDados.lista(json, a.alertas());
            if (!erros.isEmpty() && !a.aceito()) {
                itens.add(new ItemDaFila("ASSUNTO_RECUSADO", 2, true, disc.id(), disc.nome(), a.id(), a.texto(), null, null, null, erros));
            }
            if (!alertas.isEmpty() && !a.aceito()) {
                itens.add(new ItemDaFila("ALERTA_DE_PARTE", 3, true, disc.id(), disc.nome(), a.id(), a.texto(), null, null, null, alertas));
            }
        }
        for (var p : d.partes) {
            var a = d.assuntoPorId.get(p.assuntoEditalId());
            var disc = d.disciplinaPorId.get(a.disciplinaId());
            if (!p.aparece() && !"DESCARTADA".equals(p.estado())) {
                itens.add(item("NAO_APARECE_MAIS", 3, disc, a, p));
                continue;
            }
            if (!p.ativa()) continue;
            if (p.noMudou()) itens.add(item("NO_MUDOU_NA_ARVORE", 3, disc, a, p));
            else if (p.nos().isEmpty() && "PROPOSTA".equals(p.estado())) itens.add(item("PARTE_SEM_NO", 4, disc, a, p));
            else if (("MAIS_AMPLO".equals(p.natureza()) || "PARCIAL".equals(p.natureza())) && "PROPOSTA".equals(p.estado())) {
                itens.add(item("VINCULO_AMPLO_OU_PARCIAL", 5, disc, a, p));
            } else if ("REFINO".equals(p.origem()) && "PROPOSTA".equals(p.estado())) itens.add(item("NO_TROCADO_NO_REFINO", 5, disc, a, p));
        }
        var ordemDisc = new HashMap<Long, Integer>();
        d.disciplinas.forEach(x -> ordemDisc.put(x.id(), x.ordem()));
        itens.sort(Comparator.comparing((ItemDaFila i) -> !i.bloqueia()).thenComparingInt(ItemDaFila::gravidade)
            .thenComparingInt(i -> i.disciplinaId() == null ? -1 : ordemDisc.getOrDefault(i.disciplinaId(), 0))
            .thenComparing(i -> i.assuntoId() == null ? 0L : i.assuntoId()).thenComparing(i -> i.parteId() == null ? 0L : i.parteId()));
        return itens;
    }

    private ItemDaFila item(String tipo, int gravidade, Disc disc, Ass a, ConferenciaDados.Parte p) {
        return new ItemDaFila(tipo, gravidade, false, disc.id(), disc.nome(), a.id(), a.texto(), p.id(), p.nome(), null, List.of());
    }

    /** O peso de cada disciplina: o que a pessoa escolheu, ou o que D-61 calcula das questões do quadro de provas. */
    List<TipoPeso> pesos(ConferenciaDados d) {
        var calculados = PesoPorQuestoes.calcular(d.disciplinas.stream().map(Disc::questoes).toList());
        var saida = new ArrayList<TipoPeso>();
        for (int i = 0; i < d.disciplinas.size(); i++) {
            var pessoa = d.disciplinas.get(i).pesoPessoa();
            saida.add(pessoa != null ? TipoPeso.valueOf(pessoa) : calculados.get(i).peso());
        }
        return saida;
    }

    List<Disciplina> montar(ConferenciaDados d) {
        var nosPorVersao = nosDe(d);
        var pesos = PesoPorQuestoes.calcular(d.disciplinas.stream().map(Disc::questoes).toList());
        var efetivos = pesos(d);
        var saida = new ArrayList<Disciplina>();
        for (int i = 0; i < d.disciplinas.size(); i++) {
            var disc = d.disciplinas.get(i);
            var pessoa = disc.pesoPessoa() != null;
            var peso = efetivos.get(i);
            var assuntos = new ArrayList<Assunto>();
            for (var a : d.assuntosDe(disc.id())) assuntos.add(assunto(d, a, nosPorVersao));
            var ids = new ArrayList<Long>();
            ConferenciaDados.lista(json, disc.qconcursos()).forEach(x -> ids.add(Long.valueOf(x)));
            saida.add(new Disciplina(disc.id(), disc.ordem(), disc.nome(), disc.restricao(), disc.comum(), disc.questoes(), peso,
                pessoa || pesos.get(i).determinado(), pessoa, ids, disc.tentativas(), assuntos));
        }
        return saida;
    }

    private Assunto assunto(ConferenciaDados d, Ass a, Map<Long, Map<Long, No>> nosPorVersao) {
        var partes = new ArrayList<Parte>();
        for (var p : d.partesDe(a.id())) {
            var mapa = nosPorVersao.getOrDefault(p.arvoreVersaoId(), Map.of());
            partes.add(new Parte(p.id(), p.chave(), p.ordem(), p.texto(), p.contexto(), p.herdado(), p.natureza(), p.origem(),
                p.estado(), p.aparece(), p.noMudou(), p.iaMudou(), p.comentario(), resolver(p.nos(), mapa), resolver(p.propostos(), mapa), p.propNatureza()));
        }
        return new Assunto(a.id(), a.chave(), a.ordem(), a.texto(), ConferenciaDados.lista(json, a.erros()),
            ConferenciaDados.lista(json, a.alertas()), a.aceito(), a.aparece(), partes);
    }

    /** Nó que sumiu da árvore da versão continua na lista, sem caminho: a fila o mostra como "nó mudou". */
    private static List<No> resolver(List<Long> ids, Map<Long, No> mapa) {
        var saida = new ArrayList<No>();
        for (var id : ids) saida.add(mapa.getOrDefault(id, new No(id, null, List.of(), 0, 0, null)));
        return saida;
    }

    private Map<Long, Map<Long, No>> nosDe(ConferenciaDados d) {
        var porVersao = new HashMap<Long, Set<Long>>();
        for (var p : d.partes) {
            var conjunto = porVersao.computeIfAbsent(p.arvoreVersaoId(), k -> new LinkedHashSet<>());
            conjunto.addAll(p.nos());
            conjunto.addAll(p.propostos());
        }
        var saida = new HashMap<Long, Map<Long, No>>();
        porVersao.forEach((versao, ids) -> saida.put(versao, arvore.nosPorId(ids, versao)));
        return saida;
    }

    @Transactional(readOnly = true)
    public Assunto assunto(Long analiseId, Long assuntoEditalId) {
        conferencia(analiseId);
        var d = ConferenciaDados.ler(jdbc, analiseId);
        return assunto(d, assuntoDe(d, assuntoEditalId), nosDe(d));
    }

    /**
     * "Reprocessar" (docs/SPRINT-26 §3): a leitura é refeita pelo planejamento e a entrega se funde na conferência pela chave
     * (D-89). Nada é apagado e nada que a pessoa decidiu muda (D-86). Só com a conferência ainda aberta.
     */
    @Transactional
    public void reprocessar(Long analiseId) {
        var analise = analises.findById(analiseId).orElseThrow(() -> new NotFoundException("Análise não encontrada.", "ANALISE_INEXISTENTE"));
        if (analise.getStatus() != StatusAnaliseEdital.EM_CONFERENCIA) {
            throw new BusinessRuleException("Só se reprocessa uma conferência aberta: a análise está em " + analise.getStatus() + ".", "ETAPA_INVALIDA");
        }
        analise.setReprocessar(true);
        analise.setStatus(StatusAnaliseEdital.SEPARANDO_ASSUNTOS);
        analises.save(analise);
        evento(analiseId, null, "REPROCESSAR", Map.of("status", "EM_CONFERENCIA"), Map.of("status", "SEPARANDO_ASSUNTOS"));
    }

    // ================================================================ avisos depois de concluída (docs §9)

    /** As partes de um assunto já cadastrado, com o aviso de cada uma; vazio se o assunto não nasceu de uma conferência. */
    @Transactional(readOnly = true)
    public List<ConferenciaResponses.ParteDoAssunto> partesDoAssunto(Long assuntoId) {
        var analises = jdbc.queryForList("""
            SELECT d.analise_id, a.id FROM edital_assunto a JOIN edital_disciplina d ON d.id = a.disciplina_id
             WHERE a.assunto_id = ? ORDER BY d.analise_id DESC LIMIT 1""", assuntoId);
        if (analises.isEmpty()) return List.of();
        long analiseId = ((Number) analises.get(0).get("analise_id")).longValue();
        long assuntoEditalId = ((Number) analises.get(0).get("id")).longValue();
        var d = ConferenciaDados.ler(jdbc, analiseId);
        var mapas = nosDe(d);
        var saida = new ArrayList<ConferenciaResponses.ParteDoAssunto>();
        for (var p : d.partesDe(assuntoEditalId)) {
            if (!p.ativa()) continue;
            var mapa = mapas.getOrDefault(p.arvoreVersaoId(), Map.of());
            saida.add(new ConferenciaResponses.ParteDoAssunto(p.id(), analiseId, p.texto(), p.contexto(), p.herdado(), p.natureza(),
                resolver(p.nos(), mapa), aviso(p)));
        }
        return saida;
    }

    /** O que ainda pede atenção no edital do concurso: partes sem filtro de questões, vínculos largos, nós que mudaram. */
    @Transactional(readOnly = true)
    public ConferenciaResponses.AvisosDoConcurso avisosDoConcurso(Long concursoId) {
        var ids = jdbc.queryForList("SELECT id FROM analise_edital WHERE concurso_id = ? ORDER BY id DESC LIMIT 1", Long.class, concursoId);
        if (ids.isEmpty()) return new ConferenciaResponses.AvisosDoConcurso(concursoId, null, 0, 0, 0);
        var d = ConferenciaDados.ler(jdbc, ids.get(0));
        int semNo = 0, amplos = 0, mudaram = 0;
        for (var p : d.partes) {
            if (!p.ativa()) continue;
            var a = aviso(p);
            if ("SEM_NO".equals(a)) semNo++;
            else if ("MAIS_AMPLO".equals(a) || "PARCIAL".equals(a)) amplos++;
            else if ("NO_MUDOU".equals(a)) mudaram++;
        }
        return new ConferenciaResponses.AvisosDoConcurso(concursoId, ids.get(0), semNo, amplos, mudaram);
    }

    private static String aviso(ConferenciaDados.Parte p) {
        if (p.noMudou()) return "NO_MUDOU";
        if (p.nos().isEmpty()) return "SEM_NO";
        if ("MAIS_AMPLO".equals(p.natureza()) || "PARCIAL".equals(p.natureza())) return p.natureza();
        return null;
    }

    // ================================================================ ações sobre partes

    @Transactional
    public Assunto aceitar(Long analiseId, Long parteId, ConferenciaPedidos.Escopo escopo) {
        var analise = conferencia(analiseId);
        var p = parte(analiseId, parteId);
        if (p.nos().isEmpty()) {
            throw new ValidationException("A parte não tem nó para aceitar; escolha um ou declare que a árvore não tem.", "PARTE_SEM_NO", "parteId");
        }
        exigirConfirmacaoTodos(analise, p, p.nos(), p.natureza(), escopo);
        var tipoDeEscopo = escopo == null || escopo.escopo() == null ? "EDITAL" : escopo.escopo();
        if (p.noMudou()) { // reconfirmar o nó depois de a pessoa adotar a árvore nova (D-90)
            long vigente = vigente();
            for (var no : p.nos()) {
                if (!arvore.existe(no, vigente)) {
                    throw new ValidationException("O nó " + no + " saiu da árvore atual; escolha outro.", "NO_MUDOU", "nos");
                }
            }
            jdbc.update("UPDATE edital_parte SET estado = 'ACEITA', no_mudou = false, arvore_versao_id = ? WHERE id = ?", vigente, parteId);
            registrarDecisao(analise, p, p.nos(), p.natureza(), vigente, tipoDeEscopo);
            aplicarEmOutrosEditais(analise, p, p.nos(), p.natureza(), tipoDeEscopo);
            evento(analiseId, parteId, "RECONFIRMAR_NO", Map.of("nos", p.nos()), Map.of("arvore", vigente));
        } else if ("PROPOSTA".equals(p.estado())) {
            jdbc.update("UPDATE edital_parte SET estado = 'ACEITA' WHERE id = ?", parteId);
            registrarDecisao(analise, p, p.nos(), p.natureza(), p.arvoreVersaoId(), tipoDeEscopo);
            aplicarEmOutrosEditais(analise, p, p.nos(), p.natureza(), tipoDeEscopo);
            evento(analiseId, parteId, "ACEITAR", Map.of("estado", p.estado()), Map.of("estado", "ACEITA"));
        }
        return assuntoResposta(analiseId, p.assuntoEditalId());
    }

    @Transactional
    public List<Long> aceitarExatos(Long analiseId, Long disciplinaId) {
        var analise = conferencia(analiseId);
        var d = ConferenciaDados.ler(jdbc, analiseId);
        if (!d.disciplinaPorId.containsKey(disciplinaId)) throw new NotFoundException("Disciplina não encontrada nesta análise.", "DISCIPLINA_INEXISTENTE");
        var aceitas = new ArrayList<Long>();
        for (var a : d.assuntosDe(disciplinaId)) {
            for (var p : d.partesDe(a.id())) {
                if (p.ativa() && "PROPOSTA".equals(p.estado()) && "EXATO".equals(p.natureza()) && !p.nos().isEmpty() && !p.noMudou()) {
                    jdbc.update("UPDATE edital_parte SET estado = 'ACEITA' WHERE id = ?", p.id());
                    registrarDecisao(analise, p, p.nos(), p.natureza(), p.arvoreVersaoId());
                    evento(analiseId, p.id(), "ACEITAR_EXATOS", Map.of("estado", "PROPOSTA"), Map.of("estado", "ACEITA"));
                    aceitas.add(p.id());
                }
            }
        }
        return aceitas;
    }

    @Transactional
    public Assunto trocarNos(Long analiseId, Long parteId, Nos pedido) {
        var analise = conferencia(analiseId);
        var p = parte(analiseId, parteId);
        var nos = pedido.nos() == null ? List.<Long>of() : pedido.nos();
        if (nos.isEmpty()) {
            throw new ValidationException("Escolha ao menos um nó, ou declare que a árvore não tem.", "NOS_OBRIGATORIOS", "nos");
        }
        long vigente = vigente();
        validarNos(nos, pedido.natureza(), vigente);
        exigirConfirmacaoTodos(analise, p, nos, pedido.natureza(), new ConferenciaPedidos.Escopo(pedido.escopo(), pedido.confirmarTodos()));
        var tipoDeEscopo = pedido.escopo() == null ? "EDITAL" : pedido.escopo();
        guardarPropostos(p);
        jdbc.update("DELETE FROM edital_parte_no WHERE parte_id = ? AND NOT proposto", parteId);
        for (int i = 0; i < nos.size(); i++) {
            jdbc.update("INSERT INTO edital_parte_no (parte_id, proposto, ordem, no_id) VALUES (?, false, ?, ?)", parteId, i + 1, nos.get(i));
        }
        jdbc.update("UPDATE edital_parte SET natureza = ?, origem = 'PESSOA', estado = 'DECIDIDA', no_mudou = false, arvore_versao_id = ? WHERE id = ?",
            pedido.natureza(), vigente, parteId);
        registrarDecisao(analise, p, nos, pedido.natureza(), vigente, tipoDeEscopo);
        aplicarEmOutrosEditais(analise, p, nos, pedido.natureza(), tipoDeEscopo);
        evento(analiseId, parteId, "TROCAR_NOS", Map.of("nos", p.nos(), "natureza", String.valueOf(p.natureza())),
            Map.of("nos", nos, "natureza", pedido.natureza()));
        return assuntoResposta(analiseId, p.assuntoEditalId());
    }

    @Transactional
    public Assunto semNo(Long analiseId, Long parteId, ConferenciaPedidos.Escopo escopo) {
        var analise = conferencia(analiseId);
        var p = parte(analiseId, parteId);
        exigirConfirmacaoTodos(analise, p, List.of(), null, escopo);
        var tipoDeEscopo = escopo == null || escopo.escopo() == null ? "EDITAL" : escopo.escopo();
        guardarPropostos(p);
        jdbc.update("DELETE FROM edital_parte_no WHERE parte_id = ? AND NOT proposto", parteId);
        long vigente = vigente();
        jdbc.update("UPDATE edital_parte SET natureza = NULL, origem = 'PESSOA', estado = 'DECIDIDA', no_mudou = false, arvore_versao_id = ? WHERE id = ?",
            vigente, parteId);
        registrarDecisao(analise, p, List.of(), null, vigente, tipoDeEscopo);
        aplicarEmOutrosEditais(analise, p, List.of(), null, tipoDeEscopo);
        evento(analiseId, parteId, "SEM_NO", Map.of("nos", p.nos()), Map.of("nos", List.of()));
        return assuntoResposta(analiseId, p.assuntoEditalId());
    }

    @Transactional
    public Assunto descartar(Long analiseId, Long parteId, String motivo) {
        conferencia(analiseId);
        var p = parte(analiseId, parteId);
        if (motivo == null || motivo.isBlank()) {
            throw new ValidationException("Diga o motivo de descartar a parte (fora do cargo, observação, repetição).", "MOTIVO_OBRIGATORIO", "motivo");
        }
        jdbc.update("UPDATE edital_parte SET estado = 'DESCARTADA', comentario = ? WHERE id = ?", motivo.strip(), parteId);
        evento(analiseId, parteId, "DESCARTAR", Map.of("estado", p.estado()), Map.of("motivo", motivo.strip()));
        reconferir(analiseId, p.assuntoEditalId());
        return assuntoResposta(analiseId, p.assuntoEditalId());
    }

    @Transactional
    public Assunto comentar(Long analiseId, Long parteId, String texto) {
        conferencia(analiseId);
        var p = parte(analiseId, parteId);
        jdbc.update("UPDATE edital_parte SET comentario = ? WHERE id = ?", texto == null || texto.isBlank() ? null : texto.strip(), parteId);
        evento(analiseId, parteId, "COMENTAR", Map.of("comentario", String.valueOf(p.comentario())), Map.of("comentario", String.valueOf(texto)));
        return assuntoResposta(analiseId, p.assuntoEditalId());
    }

    /** Editar o texto, o prefixo herdado ou o contexto cria a parte nova (a chave muda) e deixa a antiga de lado. */
    @Transactional
    public Assunto editarParte(Long analiseId, Long parteId, TextoDaParte novo) {
        conferencia(analiseId);
        var p = parte(analiseId, parteId);
        var texto = exigirTexto(novo.texto());
        var d = ConferenciaDados.ler(jdbc, analiseId);
        long nova = inserirParte(d, p.assuntoEditalId(), p.ordem() * 1000 + 1, new TextoDaParte(texto, limpo(novo.contexto()), limpo(novo.herdado())),
            p.natureza(), p.estado(), p.arvoreVersaoId(), p.nos());
        jdbc.update("UPDATE edital_parte SET aparece = false, estado = 'DESCARTADA', comentario = 'editada' WHERE id = ?", parteId);
        evento(analiseId, nova, "EDITAR_PARTE", Map.of("texto", p.texto(), "parteAnterior", parteId), Map.of("texto", texto));
        finalizarAssunto(analiseId, p.assuntoEditalId());
        return assuntoResposta(analiseId, p.assuntoEditalId());
    }

    @Transactional
    public Assunto dividir(Long analiseId, Long parteId, Dividir pedido) {
        conferencia(analiseId);
        var p = parte(analiseId, parteId);
        if (pedido.partes() == null || pedido.partes().size() < 2) {
            throw new ValidationException("Dividir pede ao menos duas partes.", "DIVISAO_INVALIDA", "partes");
        }
        var d = ConferenciaDados.ler(jdbc, analiseId);
        int k = 0;
        var novas = new ArrayList<Long>();
        for (var t : pedido.partes()) {
            novas.add(inserirParte(d, p.assuntoEditalId(), p.ordem() * 1000 + (++k),
                new TextoDaParte(exigirTexto(t.texto()), limpo(t.contexto()), limpo(t.herdado())), p.natureza(), "PROPOSTA", p.arvoreVersaoId(), p.nos()));
            d = ConferenciaDados.ler(jdbc, analiseId); // a próxima chave conta a anterior
        }
        jdbc.update("UPDATE edital_parte SET aparece = false, estado = 'DESCARTADA', comentario = 'dividida' WHERE id = ?", parteId);
        evento(analiseId, parteId, "DIVIDIR", Map.of("texto", p.texto()), Map.of("partes", novas));
        finalizarAssunto(analiseId, p.assuntoEditalId());
        return assuntoResposta(analiseId, p.assuntoEditalId());
    }

    @Transactional
    public Assunto juntar(Long analiseId, Long parteId, Juntar pedido) {
        conferencia(analiseId);
        var a = parte(analiseId, parteId);
        if (pedido.outraParteId() == null || pedido.outraParteId().equals(parteId)) {
            throw new ValidationException("Escolha a outra parte a juntar.", "JUNCAO_INVALIDA", "outraParteId");
        }
        var b = parte(analiseId, pedido.outraParteId());
        if (a.assuntoEditalId() != b.assuntoEditalId()) {
            throw new ValidationException("Só se juntam partes do mesmo assunto.", "JUNCAO_INVALIDA", "outraParteId");
        }
        var nos = new ArrayList<Long>(new LinkedHashSet<>(concat(a.nos(), b.nos())));
        if (nos.size() > 3) nos = new ArrayList<>(nos.subList(0, 3));
        var natureza = nos.isEmpty() ? null : mais_ampla(a.natureza(), b.natureza());
        var d = ConferenciaDados.ler(jdbc, analiseId);
        long nova = inserirParte(d, a.assuntoEditalId(), Math.min(a.ordem(), b.ordem()) * 1000 + 1,
            new TextoDaParte(exigirTexto(pedido.texto()), limpo(pedido.contexto()), limpo(pedido.herdado())), natureza, "PROPOSTA", a.arvoreVersaoId(), nos);
        jdbc.update("UPDATE edital_parte SET aparece = false, estado = 'DESCARTADA', comentario = 'juntada' WHERE id IN (?, ?)", parteId, b.id());
        evento(analiseId, nova, "JUNTAR", Map.of("partes", List.of(parteId, b.id())), Map.of("texto", pedido.texto()));
        finalizarAssunto(analiseId, a.assuntoEditalId());
        return assuntoResposta(analiseId, a.assuntoEditalId());
    }

    // ================================================================ ações sobre assuntos, disciplinas e trechos

    @Transactional
    public Assunto aceitarAssunto(Long analiseId, Long assuntoEditalId) {
        conferencia(analiseId);
        var d = ConferenciaDados.ler(jdbc, analiseId);
        assuntoDe(d, assuntoEditalId);
        jdbc.update("UPDATE edital_assunto SET aceito = true WHERE id = ?", assuntoEditalId);
        evento(analiseId, null, "ACEITAR_ASSUNTO", Map.of("assunto", assuntoEditalId), Map.of("aceito", true));
        return assuntoResposta(analiseId, assuntoEditalId);
    }

    /** Corrigir o texto do assunto: a chave e a conferência do assunto se refazem; as partes ficam como estão. */
    @Transactional
    public Assunto editarAssunto(Long analiseId, Long assuntoEditalId, String texto) {
        conferencia(analiseId);
        var d = ConferenciaDados.ler(jdbc, analiseId);
        var a = assuntoDe(d, assuntoEditalId);
        var novo = exigirTexto(texto);
        var disc = d.disciplinaPorId.get(a.disciplinaId());
        var base = String.join("|", RegrasEdital.normalizar(disc.nome()), RegrasEdital.normalizar(novo));
        int n = 1;
        for (var outro : d.assuntosDe(disc.id())) {
            if (outro.id() != a.id() && String.join("|", RegrasEdital.normalizar(disc.nome()), RegrasEdital.normalizar(outro.texto())).equals(base)) n++;
        }
        var chave = RegrasEdital.chaveDoAssunto(disc.nome(), novo, new HashMap<>(Map.of(base, n - 1)));
        jdbc.update("UPDATE edital_assunto SET texto = ?, chave = ?, aceito = false WHERE id = ?", novo, chave, assuntoEditalId);
        evento(analiseId, null, "EDITAR_ASSUNTO", Map.of("texto", a.texto()), Map.of("texto", novo));
        reconferir(analiseId, assuntoEditalId);
        return assuntoResposta(analiseId, assuntoEditalId);
    }

    @Transactional
    public void trocarPeso(Long analiseId, Long disciplinaId, TipoPeso peso) {
        conferencia(analiseId);
        var d = ConferenciaDados.ler(jdbc, analiseId);
        var disc = d.disciplinaPorId.get(disciplinaId);
        if (disc == null) throw new NotFoundException("Disciplina não encontrada nesta análise.", "DISCIPLINA_INEXISTENTE");
        jdbc.update("UPDATE edital_disciplina SET peso_pessoa = ? WHERE id = ?", peso == null ? null : peso.name(), disciplinaId);
        evento(analiseId, null, "PESO", Map.of("peso", String.valueOf(disc.pesoPessoa())), Map.of("peso", String.valueOf(peso)));
    }

    @Transactional
    public void resolverTrecho(Long analiseId, Long trechoId, ResolverTrecho pedido) {
        conferencia(analiseId);
        var d = ConferenciaDados.ler(jdbc, analiseId);
        var t = d.trechos.stream().filter(x -> x.id() == trechoId).findFirst()
            .orElseThrow(() -> new NotFoundException("Trecho não encontrado nesta análise.", "TRECHO_INEXISTENTE"));
        if (!"ABERTO".equals(t.estado())) throw new BusinessRuleException("Este trecho já foi resolvido.", "TRECHO_JA_RESOLVIDO");
        if ("DESCARTAR".equals(pedido.acao())) {
            if (pedido.motivo() == null || pedido.motivo().isBlank()) {
                throw new ValidationException("Diga o motivo de descartar o trecho.", "MOTIVO_OBRIGATORIO", "motivo");
            }
            jdbc.update("UPDATE edital_trecho SET estado = 'DESCARTADO', motivo = ? WHERE id = ?", pedido.motivo().strip(), trechoId);
            evento(analiseId, null, "TRECHO_DESCARTADO", Map.of("trecho", trechoId), Map.of("motivo", pedido.motivo().strip()));
        } else if ("INCLUIR".equals(pedido.acao())) {
            incluirTrecho(analiseId, d, t, pedido);
        } else {
            throw new ValidationException("A ação é DESCARTAR ou INCLUIR.", "ACAO_INVALIDA", "acao");
        }
    }

    private void incluirTrecho(long analiseId, ConferenciaDados d, ConferenciaDados.Trecho t, ResolverTrecho pedido) {
        var texto = exigirTexto(pedido.texto() != null ? pedido.texto() : String.join(" ", ConferenciaDados.lista(json, t.linhas())));
        Long disciplinaId = t.disciplinaId() != null ? t.disciplinaId() : pedido.disciplinaId();
        if (disciplinaId == null && pedido.novaDisciplina() != null && !pedido.novaDisciplina().isBlank()) {
            disciplinaId = jdbc.queryForObject("""
                INSERT INTO edital_disciplina (analise_id, ordem, nome, texto) VALUES (?, ?, ?, ?) RETURNING id""", Long.class,
                analiseId, d.disciplinas.size() + 1, pedido.novaDisciplina().strip(), texto);
        }
        if (disciplinaId == null || (!d.disciplinaPorId.containsKey(disciplinaId) && !existeDisciplina(analiseId, disciplinaId))) {
            throw new ValidationException("Diga em que disciplina incluir (disciplinaId) ou o nome de uma nova (novaDisciplina).", "DISCIPLINA_OBRIGATORIA", "disciplinaId");
        }
        var disc = jdbc.queryForMap("SELECT nome FROM edital_disciplina WHERE id = ?", disciplinaId);
        int ordem = 1 + jdbc.queryForObject("SELECT COALESCE(max(ordem), 0) FROM edital_assunto WHERE disciplina_id = ?", Integer.class, disciplinaId);
        var chave = RegrasEdital.chaveDoAssunto((String) disc.get("nome"), texto, new HashMap<>());
        var unica = chave;
        for (int n = 2; jdbc.queryForObject("SELECT count(*) FROM edital_assunto WHERE disciplina_id = ? AND chave = ?", Integer.class, disciplinaId, unica) > 0; n++) {
            unica = RegrasEdital.chaveDoAssunto((String) disc.get("nome"), texto, new HashMap<>(Map.of(String.join("|", RegrasEdital.normalizar((String) disc.get("nome")), RegrasEdital.normalizar(texto)), n - 1)));
        }
        long assuntoId = jdbc.queryForObject("""
            INSERT INTO edital_assunto (disciplina_id, chave, ordem, texto) VALUES (?, ?, ?, ?) RETURNING id""", Long.class, disciplinaId, unica, ordem, texto);
        long versao = jdbc.queryForObject("SELECT arvore_versao_id FROM edital_execucao WHERE analise_id = ? ORDER BY id DESC LIMIT 1", Long.class, analiseId);
        inserirParte(ConferenciaDados.ler(jdbc, analiseId), assuntoId, 1000, new TextoDaParte(texto, null, null), null, "PROPOSTA", versao, List.of());
        jdbc.update("UPDATE edital_trecho SET estado = 'INCLUIDO' WHERE id = ?", t.id());
        evento(analiseId, null, "TRECHO_INCLUIDO", Map.of("trecho", t.id()), Map.of("assunto", assuntoId));
        finalizarAssunto(analiseId, assuntoId);
    }

    private boolean existeDisciplina(long analiseId, long disciplinaId) {
        return jdbc.queryForObject("SELECT count(*) FROM edital_disciplina WHERE id = ? AND analise_id = ?", Integer.class, disciplinaId, analiseId) > 0;
    }

    // ================================================================ apoio

    /** A análise precisa ter a conferência entregue (EM_CONFERENCIA) ou já concluída (CONFIRMADA). */
    AnaliseEdital conferencia(Long analiseId) {
        var a = analises.findById(analiseId).orElseThrow(() -> new NotFoundException("Análise não encontrada.", "ANALISE_INEXISTENTE"));
        if (a.getStatus() != StatusAnaliseEdital.EM_CONFERENCIA && a.getStatus() != StatusAnaliseEdital.CONFIRMADA) {
            throw new BusinessRuleException("A conferência ainda não existe: a análise está em " + a.getStatus() + ".", "ETAPA_INVALIDA");
        }
        return a;
    }

    private ConferenciaDados.Parte parte(Long analiseId, Long parteId) {
        var d = ConferenciaDados.ler(jdbc, analiseId);
        return d.partes.stream().filter(p -> p.id() == parteId).findFirst()
            .orElseThrow(() -> new NotFoundException("Parte não encontrada nesta análise.", "PARTE_INEXISTENTE"));
    }

    private Ass assuntoDe(ConferenciaDados d, Long assuntoEditalId) {
        var a = d.assuntoPorId.get(assuntoEditalId);
        if (a == null) throw new NotFoundException("Assunto não encontrado nesta análise.", "ASSUNTO_INEXISTENTE");
        return a;
    }

    private Assunto assuntoResposta(Long analiseId, long assuntoEditalId) {
        var d = ConferenciaDados.ler(jdbc, analiseId);
        return assunto(d, d.assuntoPorId.get(assuntoEditalId), nosDe(d));
    }

    private void validarNos(List<Long> nos, String natureza, long versaoId) {
        if (nos.size() > 3 || new LinkedHashSet<>(nos).size() != nos.size()) {
            throw new ValidationException("Uma parte liga a até 3 nós, sem repetir.", "NOS_INVALIDOS", "nos");
        }
        if (natureza == null || !NATUREZAS.contains(natureza)) {
            throw new ValidationException("A natureza do vínculo é EXATO, MAIS_AMPLO ou PARCIAL.", "NATUREZA_INVALIDA", "natureza");
        }
        for (var no : nos) {
            if (no < 0) throw new ValidationException("Escolha um assunto da árvore, não a disciplina inteira.", "NO_DE_DISCIPLINA", "nos");
            if (!arvore.existe(no, versaoId)) throw new ValidationException("O nó " + no + " não existe na árvore desta análise.", "NO_INEXISTENTE", "nos");
        }
    }

    /** Antes de a pessoa trocar a ligação, o que o sistema propôs fica guardado como sugestão (só da primeira vez). */
    private void guardarPropostos(ConferenciaDados.Parte p) {
        if (!p.propostos().isEmpty()) return;
        jdbc.update("UPDATE edital_parte_no SET proposto = true WHERE parte_id = ? AND NOT proposto", p.id());
    }

    private long inserirParte(ConferenciaDados d, long assuntoEditalId, int ordem, TextoDaParte t, String natureza, String estado,
                              long versaoId, List<Long> nos) {
        var a = d.assuntoPorId.get(assuntoEditalId);
        var disc = d.disciplinaPorId.get(a.disciplinaId());
        var base = RegrasEdital.baseDaParte(disc.nome(), a.texto(), t.herdado(), t.texto(), t.contexto());
        int n = 1;
        for (var irma : d.partesDe(assuntoEditalId)) {
            if (irma.ativa() && RegrasEdital.baseDaParte(disc.nome(), a.texto(), irma.herdado(), irma.texto(), irma.contexto()).equals(base)) n++;
        }
        var existentes = new java.util.HashSet<String>();
        d.partesDe(assuntoEditalId).forEach(x -> existentes.add(x.chave()));
        var chave = RegrasEdital.chaveDaBase(base, n);
        while (existentes.contains(chave)) chave = RegrasEdital.chaveDaBase(base, ++n);
        long id = jdbc.queryForObject("""
            INSERT INTO edital_parte (assunto_edital_id, chave, ordem, texto, contexto, herdado, natureza, origem, estado, arvore_versao_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'PESSOA', ?, ?) RETURNING id""", Long.class, assuntoEditalId, chave, ordem, t.texto(), t.contexto(),
            t.herdado(), nos.isEmpty() ? null : natureza, estado, versaoId);
        for (int i = 0; i < nos.size(); i++) {
            jdbc.update("INSERT INTO edital_parte_no (parte_id, proposto, ordem, no_id) VALUES (?, false, ?, ?)", id, i + 1, nos.get(i));
        }
        return id;
    }

    private void finalizarAssunto(long analiseId, long assuntoEditalId) {
        jdbc.update("""
            UPDATE edital_parte SET ordem = r.n FROM (
                SELECT id, row_number() OVER (ORDER BY ordem, id) AS n FROM edital_parte
                 WHERE assunto_edital_id = ? AND aparece AND estado <> 'DESCARTADA') r
             WHERE edital_parte.id = r.id""", assuntoEditalId);
        reconferir(analiseId, assuntoEditalId);
    }

    /** Refaz a conferência por regra do assunto com as partes de hoje (docs §3). Os alertas da IA se perdem; os da regra voltam. */
    private void reconferir(long analiseId, long assuntoEditalId) {
        var d = ConferenciaDados.ler(jdbc, analiseId);
        var a = d.assuntoPorId.get(assuntoEditalId);
        var disc = d.disciplinaPorId.get(a.disciplinaId());
        var partes = d.partesDe(assuntoEditalId).stream().filter(ConferenciaDados.Parte::ativa)
            .map(p -> new ParteRegra(p.texto(), p.contexto(), p.herdado())).toList();
        var referencias = ConferenciaDados.lista(json, disc.referencias());
        var alertas = new ArrayList<String>();
        var erros = RegrasEdital.conferirAssunto(new AssuntoRegra(a.texto(), partes), disc.texto() == null ? a.texto() : disc.texto(),
            disc.nome() + " " + (disc.restricao() == null ? "" : disc.restricao()), referencias, alertas);
        jdbc.update("UPDATE edital_assunto SET erros = ?, alertas = ? WHERE id = ?", json.writeValueAsString(erros), json.writeValueAsString(alertas), assuntoEditalId);
    }

    // ---------------------------------------------------------------- decisões e eventos

    private long vigente() {
        var id = arvore.idDaVigente();
        if (id == null) throw new NotFoundException("Nenhuma árvore do QConcursos foi carregada ainda.", "ARVORE_NAO_CARREGADA");
        return id;
    }

    private void registrarDecisao(AnaliseEdital analise, ConferenciaDados.Parte p, List<Long> nos, String natureza, long versaoId) {
        registrarDecisao(analise, p, nos, natureza, versaoId, "EDITAL");
    }

    // ---------------------------------------------------------------- escopo "todos os editais" (D-87)

    /**
     * As partes de outros editais que "valer para todos" mudaria: mesma chave (as palavras da parte e do contexto), ainda por
     * conferir (a decisão da pessoa nunca é tocada, D-86) e com ligação diferente da nova.
     */
    List<Long> afetadasEmOutrosEditais(long analiseId, ConferenciaDados.Parte p, List<Long> nos, String natureza) {
        var chave = RegrasEdital.chaveNormalizada(p.nome());
        var chaveContexto = RegrasEdital.chaveNormalizada(p.contexto() == null ? "" : p.contexto());
        var saida = new ArrayList<Long>();
        var linhas = jdbc.queryForList("""
            SELECT p.id, p.texto, p.herdado, p.contexto, p.natureza FROM edital_parte p
              JOIN edital_assunto a ON a.id = p.assunto_edital_id JOIN edital_disciplina d ON d.id = a.disciplina_id
             WHERE d.analise_id <> ? AND p.aparece AND p.estado = 'PROPOSTA'""", analiseId);
        for (var l : linhas) {
            var herdado = (String) l.get("herdado");
            var nome = herdado == null || herdado.isBlank() ? (String) l.get("texto") : herdado + " " + l.get("texto");
            var contexto = (String) l.get("contexto");
            if (!chave.equals(RegrasEdital.chaveNormalizada(nome)) || !chaveContexto.equals(RegrasEdital.chaveNormalizada(contexto == null ? "" : contexto))) continue;
            long id = ((Number) l.get("id")).longValue();
            var atuais = jdbc.queryForList("SELECT no_id FROM edital_parte_no WHERE parte_id = ? AND NOT proposto ORDER BY ordem", Long.class, id);
            boolean igual = new java.util.HashSet<>(atuais).equals(new java.util.HashSet<>(nos))
                && java.util.Objects.equals(natureza, nos.isEmpty() ? null : l.get("natureza"));
            if (!igual) saida.add(id);
        }
        return saida;
    }

    /** O que "valer para todos" mudaria, para a tela mostrar antes de a pessoa confirmar. */
    @Transactional(readOnly = true)
    public ConferenciaResponses.EscopoTodos previaDeTodos(Long analiseId, Long parteId, List<Long> nos, String natureza) {
        conferencia(analiseId);
        var p = parte(analiseId, parteId);
        var afetadas = afetadasEmOutrosEditais(analiseId, p, nos == null ? List.of() : nos, natureza);
        var exemplos = new ArrayList<String>();
        for (var id : afetadas.stream().limit(5).toList()) {
            exemplos.add(jdbc.queryForObject("""
                SELECT c.nome || ' · ' || p.texto FROM edital_parte p
                  JOIN edital_assunto a ON a.id = p.assunto_edital_id JOIN edital_disciplina d ON d.id = a.disciplina_id
                  JOIN analise_edital c ON c.id = d.analise_id WHERE p.id = ?""".replace("c.nome", "COALESCE(c.concurso_nome, c.nome_arquivo)"), String.class, id));
        }
        return new ConferenciaResponses.EscopoTodos(afetadas.size(), exemplos);
    }

    private void exigirConfirmacaoTodos(AnaliseEdital analise, ConferenciaDados.Parte p, List<Long> nos, String natureza, ConferenciaPedidos.Escopo escopo) {
        if (escopo == null || escopo.escopo() == null || "EDITAL".equals(escopo.escopo())) return;
        if (!"TODOS".equals(escopo.escopo())) {
            throw new ValidationException("O escopo é EDITAL ou TODOS.", "ESCOPO_INVALIDO", "escopo");
        }
        if (!Boolean.TRUE.equals(escopo.confirmarTodos())) {
            int n = afetadasEmOutrosEditais(analise.getId(), p, nos, natureza).size();
            throw new BusinessRuleException("Valer para todos os editais muda " + n + " parte(s) de outros editais, ainda por conferir. Confirme para continuar.",
                "CONFIRMACAO_TODOS");
        }
    }

    /** Escopo TODOS: as partes de outros editais, ainda por conferir, recebem a ligação e ficam decididas (origem DECISAO). */
    private void aplicarEmOutrosEditais(AnaliseEdital analise, ConferenciaDados.Parte p, List<Long> nos, String natureza, String escopo) {
        if (!"TODOS".equals(escopo)) return;
        long vigente = vigente();
        for (var id : afetadasEmOutrosEditais(analise.getId(), p, nos, natureza)) {
            var analiseDaParte = jdbc.queryForObject("""
                SELECT d.analise_id FROM edital_parte x JOIN edital_assunto a ON a.id = x.assunto_edital_id
                  JOIN edital_disciplina d ON d.id = a.disciplina_id WHERE x.id = ?""", Long.class, id);
            jdbc.update("DELETE FROM edital_parte_no WHERE parte_id = ? AND NOT proposto", id);
            for (int i = 0; i < nos.size(); i++) {
                jdbc.update("INSERT INTO edital_parte_no (parte_id, proposto, ordem, no_id) VALUES (?, false, ?, ?)", id, i + 1, nos.get(i));
            }
            jdbc.update("UPDATE edital_parte SET natureza = ?, origem = 'DECISAO', estado = 'DECIDIDA', no_mudou = false, arvore_versao_id = ? WHERE id = ?",
                nos.isEmpty() ? null : natureza, vigente, id);
            evento(analiseDaParte, id, "DECISAO_TODOS", Map.of("origem", "análise " + analise.getId()), Map.of("nos", nos));
        }
    }

    private void registrarDecisao(AnaliseEdital analise, ConferenciaDados.Parte p, List<Long> nos, String natureza, long versaoId, String escopo) {
        var texto = p.nome();
        var contexto = p.contexto() == null ? "" : p.contexto();
        var chave = RegrasEdital.chaveNormalizada(texto);
        var chaveContexto = RegrasEdital.chaveNormalizada(contexto);
        boolean todos = "TODOS".equals(escopo);
        var ids = todos
            ? jdbc.queryForList("SELECT id FROM edital_decisao WHERE ativa AND escopo = 'TODOS' AND parte_chave_norm = ? AND contexto_norm = ?", Long.class, chave, chaveContexto)
            : jdbc.queryForList("SELECT id FROM edital_decisao WHERE ativa AND escopo = 'EDITAL' AND analise_id = ? AND parte_chave_norm = ? AND contexto_norm = ?",
                Long.class, analise.getId(), chave, chaveContexto);
        if (todos) { // promover a TODOS substitui a decisão só deste edital para a mesma chave
            jdbc.update("UPDATE edital_decisao SET ativa = false, revogada_em = now() WHERE ativa AND escopo = 'EDITAL' AND analise_id = ? AND parte_chave_norm = ? AND contexto_norm = ?",
                analise.getId(), chave, chaveContexto);
        }
        var nosJson = json.writeValueAsString(nos);
        if (ids.isEmpty()) {
            jdbc.update("""
                INSERT INTO edital_decisao (parte_texto, contexto_texto, parte_chave_norm, contexto_norm, nos, natureza, escopo, analise_id, arvore_versao_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", texto, contexto, chave, chaveContexto, nosJson, natureza, todos ? "TODOS" : "EDITAL", analise.getId(), versaoId);
        } else {
            jdbc.update("UPDATE edital_decisao SET nos = ?, natureza = ?, arvore_versao_id = ? WHERE id = ?", nosJson, natureza, versaoId, ids.get(0));
        }
    }

    void evento(long analiseId, Long parteId, String tipo, Object antes, Object depois) {
        jdbc.update("INSERT INTO edital_evento (analise_id, parte_id, tipo, antes, depois) VALUES (?, ?, ?, ?, ?)",
            analiseId, parteId, tipo, json.writeValueAsString(antes), json.writeValueAsString(depois));
    }

    private static String exigirTexto(String texto) {
        if (texto == null || texto.isBlank()) throw new ValidationException("O texto não pode ficar vazio.", "TEXTO_OBRIGATORIO", "texto");
        return texto.strip().replaceAll("\\s+", " ");
    }

    private static String limpo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.strip().replaceAll("\\s+", " ");
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        var saida = new ArrayList<T>(a);
        saida.addAll(b);
        return saida;
    }

    /** MAIS_AMPLO vale mais que PARCIAL, que vale mais que EXATO: juntar nunca deixa o vínculo parecer mais exato. */
    private static String mais_ampla(String a, String b) {
        var ordem = List.of("EXATO", "PARCIAL", "MAIS_AMPLO");
        int ia = a == null ? -1 : ordem.indexOf(a);
        int ib = b == null ? -1 : ordem.indexOf(b);
        return ordem.get(Math.max(0, Math.max(ia, ib)));
    }
}
