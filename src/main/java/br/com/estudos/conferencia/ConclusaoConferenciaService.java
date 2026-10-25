package br.com.estudos.conferencia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.conferencia.ConferenciaDados.Parte;
import br.com.estudos.conferencia.ConferenciaResponses.ItemDaFila;
import br.com.estudos.edital.AnaliseEditalRepository;
import br.com.estudos.edital.ConclusaoEditalService;
import br.com.estudos.edital.ConclusaoEditalService.PlanoConclusao;
import br.com.estudos.edital.EditalArquivos;
import br.com.estudos.shared.enums.StatusAnaliseEdital;
import br.com.estudos.shared.exception.BusinessRuleException;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Concluir a conferência (01_DOMINIO §3.12, D-62, D-91, D-92; docs/SPRINT-25-CONFERENCIA-EDITAL.md §7). Bloqueia só o que
 * deixa o conteúdo do edital incerto; parte sem nó ou com vínculo mais amplo avisa e deixa passar. É aqui, e só aqui, que
 * disciplinas, assuntos e concurso passam a existir.
 */
@Service
public class ConclusaoConferenciaService {

    private final JdbcTemplate jdbc;
    private final AnaliseEditalRepository analises;
    private final ConferenciaService conferencia;
    private final ConclusaoEditalService conclusao;
    private final EditalArquivos arquivos;

    public ConclusaoConferenciaService(JdbcTemplate jdbc, AnaliseEditalRepository analises, ConferenciaService conferencia,
                                       ConclusaoEditalService conclusao, EditalArquivos arquivos) {
        this.jdbc = jdbc;
        this.analises = analises;
        this.conferencia = conferencia;
        this.conclusao = conclusao;
        this.arquivos = arquivos;
    }

    @Transactional
    public Map<String, Object> concluir(Long analiseId) {
        var analise = analises.findById(analiseId).orElseThrow(() -> new NotFoundException("Análise não encontrada.", "ANALISE_INEXISTENTE"));
        if (analise.getStatus() == StatusAnaliseEdital.CONFIRMADA) {
            throw new BusinessRuleException("Esta conferência já foi concluída.", "JA_CONCLUIDA");
        }
        if (analise.getStatus() != StatusAnaliseEdital.EM_CONFERENCIA) {
            throw new BusinessRuleException("A conferência ainda não existe: a análise está em " + analise.getStatus() + ".", "ETAPA_INVALIDA");
        }
        var dados = ConferenciaDados.ler(jdbc, analiseId);
        var bloqueios = conferencia.fila(dados).stream().filter(ItemDaFila::bloqueia).toList();
        if (!bloqueios.isEmpty()) {
            var porTipo = new LinkedHashMap<String, Integer>();
            bloqueios.forEach(b -> porTipo.merge(b.tipo(), 1, Integer::sum));
            throw new ValidationException("A conferência tem divergências que deixam o conteúdo do edital incerto (D-91): " + porTipo
                + ". Resolva-as na fila.", "CONFERENCIA_COM_BLOQUEIO", "fila");
        }

        // O que entra: assunto que aparece e tem ao menos uma parte viva; disciplina que sobra com algum assunto.
        var pesos = conferencia.pesos(dados);
        var disciplinas = new ArrayList<PlanoConclusao.Disciplina>();
        var ordemDosAssuntos = new ArrayList<Long>(); // os edital_assunto.id, na ordem em que o plano os lista
        int semNo = 0;
        for (int i = 0; i < dados.disciplinas.size(); i++) {
            var disc = dados.disciplinas.get(i);
            var assuntos = new ArrayList<PlanoConclusao.Assunto>();
            for (var a : dados.assuntosDe(disc.id())) {
                if (!a.aparece()) continue;
                List<Parte> vivas = dados.partesDe(a.id()).stream().filter(Parte::ativa).toList();
                if (vivas.isEmpty()) continue;
                assuntos.add(new PlanoConclusao.Assunto(a.texto(), null));
                ordemDosAssuntos.add(a.id());
                semNo += (int) vivas.stream().filter(p -> p.nos().isEmpty()).count();
            }
            if (!assuntos.isEmpty()) disciplinas.add(new PlanoConclusao.Disciplina(disc.nome(), null, pesos.get(i), assuntos));
        }
        if (disciplinas.isEmpty()) {
            throw new ValidationException("A conferência ficou sem nenhum assunto.", "PROPOSTA_VAZIA", "disciplinas");
        }

        var resultado = conclusao.concluir(analise, new PlanoConclusao(
            analise.getConcursoNome() == null ? "Concurso " + analiseId : analise.getConcursoNome(),
            analise.getCargoEscolhido(), analise.getBanca(), analise.getDataProva(), disciplinas));
        for (int i = 0; i < ordemDosAssuntos.size(); i++) {
            jdbc.update("UPDATE edital_assunto SET assunto_id = ? WHERE id = ?", resultado.assuntos().get(i).getId(), ordemDosAssuntos.get(i));
        }
        analise.setPdfArquivo(null);
        analises.save(analise);
        arquivos.apagar(analiseId);
        conferencia.evento(analiseId, null, "CONCLUIR", Map.of("status", "EM_CONFERENCIA"),
            Map.of("concurso", resultado.concurso().getId(), "assuntos", ordemDosAssuntos.size(), "partesSemNo", semNo));
        return Map.of("concursoId", resultado.concurso().getId(), "assuntos", ordemDosAssuntos.size(), "partesSemNo", semNo);
    }
}
