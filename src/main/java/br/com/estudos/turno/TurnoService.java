package br.com.estudos.turno;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoMapper;
import br.com.estudos.frente.FrenteService;
import br.com.estudos.revisao.RevisaoRepository;
import br.com.estudos.shared.parametro.ParametroRepository;

/**
 * O plano do turno de hoje — fila de recuperação + bloco de conteúdo,
 * derivado e efêmero (01_DOMINIO §7.4). docs/SPRINT-6-TURNO.md §2.
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

        var fila = revisaoRepository.listarVencidasPorAtraso(hoje).stream()
            .limit(tetoDiario)
            .map(r -> new FilaRecuperacaoItemResponse(
                r.getAssunto().getId(),
                r.getAssunto().getNome(),
                r.getNivel(),
                r.getDataPrevista(),
                ChronoUnit.DAYS.between(r.getDataPrevista(), hoje)))
            .toList();

        var blocoConteudo = frenteService.proximaSugestaoDeConteudo()
            .map(AssuntoMapper::toResponse)
            .orElse(null);

        return new TurnoPlanoResponse(fila, blocoConteudo);
    }
}
