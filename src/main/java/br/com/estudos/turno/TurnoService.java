package br.com.estudos.turno;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoMapper;
import br.com.estudos.frente.FrenteService;
import br.com.estudos.frente.SugestaoConteudo;
import br.com.estudos.revisao.RevisaoRepository;
import br.com.estudos.shared.parametro.ParametroRepository;

/**
 * D-27 — devolve **plano de turno** (fila + reestudo + bloco), nunca "próximo assunto"
 * sozinho. O plano é derivado e efêmero (D-28, 01_DOMINIO §7.4).
 * docs/SPRINT-6-TURNO.md §2.
 */
@Service
public class TurnoService {

    private final RevisaoRepository revisaoRepository;
    private final FrenteService frenteService;
    private final ParametroRepository parametroRepository;
    private final Clock clock;

    public TurnoService(
            RevisaoRepository revisaoRepository,
            FrenteService frenteService,
            ParametroRepository parametroRepository,
            Clock clock) {
        this.revisaoRepository = revisaoRepository;
        this.frenteService = frenteService;
        this.parametroRepository = parametroRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TurnoPlanoResponse plano() {
        var hoje = LocalDate.now(clock);
        var tetoDiario = parametroRepository.findById("teto_diario_recuperacoes")
            .map(p -> Integer.parseInt(p.getValor()))
            .orElseThrow(() -> new IllegalStateException("Parâmetro obrigatório ausente: teto_diario_recuperacoes"));

        // D-15: mais atrasada primeiro (listarVencidasPorAtraso já ordena), cortada no teto.
        // D-40: o teto limita o que é OFERECIDO aqui, nunca o que é permitido — nenhum
        // endpoint bloqueia registrar mais que isto; "puxar mais" é só chamar de novo
        // depois de registrar (emergente, sem endpoint dedicado, docs/SPRINT-6-TURNO.md §2).
        var fila = revisaoRepository.listarVencidasPorAtraso(hoje).stream()
            .limit(tetoDiario)
            .map(r -> new FilaRecuperacaoItemResponse(
                r.getAssunto().getId(),
                r.getAssunto().getNome(),
                r.getNivel(),
                r.getDataPrevista(),
                ChronoUnit.DAYS.between(r.getDataPrevista(), hoje)))
            .toList();

        // D-07/D-11: corrigir vem antes de abrir — a tela mostra esta lista
        // antes do bloco de conteúdo novo (01_DOMINIO §7.4, v1.19.0).
        var reestudos = revisaoRepository.listarEmReestudo().stream()
            .map(r -> new ReestudoItemResponse(
                r.getAssunto().getId(),
                r.getAssunto().getNome(),
                r.getNivel(),
                r.getSessaoOrigem().getData()))
            .toList();

        // D-55: continuar a leitura antes de abrir assunto novo (docs/SPRINT-17-USO-REAL.md §1.2).
        var sugestao = frenteService.sugestaoDeConteudo();
        var blocoConteudo = sugestao.map(sg -> AssuntoMapper.toResponse(sg.assunto())).orElse(null);
        var segmento = sugestao
            .filter(sg -> sg.segmento() != null)
            .map(sg -> new SegmentoSugeridoResponse(
                sg.segmento().getId(), sg.segmento().getOrdem(), sg.totalSegmentos(), sg.segmento().getArquivo(),
                sg.segmento().getPaginaInicial(), sg.segmento().getPaginaFinal(), sg.segmento().getTempoEstimadoMin()))
            .orElse(null);
        var continuacao = sugestao.map(SugestaoConteudo::continuacao).orElse(false);

        return new TurnoPlanoResponse(fila, reestudos, blocoConteudo, continuacao, segmento);
    }
}
