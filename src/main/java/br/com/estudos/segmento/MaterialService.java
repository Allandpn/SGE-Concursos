package br.com.estudos.segmento;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.shared.exception.NotFoundException;
import br.com.estudos.shared.exception.ValidationException;

/**
 * Estado do material de leitura (01_DOMINIO §3.10, docs/SPRINT-19-MATERIAL-AUTOMATICO.md §5).
 * Derivado dos segmentos (D-16), com uma exceção registrada: a lacuna (D-65).
 * Segmento vence lacuna — o assunto que ganhou material está pronto, mesmo que
 * uma tentativa anterior tenha registrado lacuna.
 */
@Service
public class MaterialService {

    public enum Situacao { PRONTO, SEM_MATERIAL, A_PREPARAR }

    public record Estado(int segmentos, String lacuna) {
        public Situacao situacao() {
            if (segmentos > 0) return Situacao.PRONTO;
            return lacuna != null ? Situacao.SEM_MATERIAL : Situacao.A_PREPARAR;
        }
    }

    public record Resumo(long prontos, long semMaterial, long aPreparar) {}

    private final SegmentoRepository segmentoRepository;
    private final LacunaMaterialRepository lacunaRepository;
    private final AssuntoRepository assuntoRepository;
    private final Clock clock;

    public MaterialService(
            SegmentoRepository segmentoRepository,
            LacunaMaterialRepository lacunaRepository,
            AssuntoRepository assuntoRepository,
            Clock clock) {
        this.segmentoRepository = segmentoRepository;
        this.lacunaRepository = lacunaRepository;
        this.assuntoRepository = assuntoRepository;
        this.clock = clock;
    }

    /** Duas consultas para o conjunto inteiro, como as fases (Sprint 16 §3). */
    @Transactional(readOnly = true)
    public Map<Long, Estado> estados(Collection<Long> assuntoIds) {
        var estados = new HashMap<Long, Estado>();
        if (assuntoIds.isEmpty()) return estados;
        var contagens = new HashMap<Long, Integer>();
        for (var linha : segmentoRepository.contarPorAssunto(assuntoIds)) {
            contagens.put((Long) linha[0], ((Long) linha[1]).intValue());
        }
        var lacunas = new HashMap<Long, String>();
        for (var lacuna : lacunaRepository.findByAssuntoIdInAndDescartadaEmIsNull(assuntoIds)) {
            lacunas.put(lacuna.getAssuntoId(), lacuna.getMotivo());
        }
        for (var id : assuntoIds) {
            estados.put(id, new Estado(contagens.getOrDefault(id, 0), lacunas.get(id)));
        }
        return estados;
    }

    public record Volume(long minutos, long paginas) {}

    /** §5A — minutos e páginas de leitura por assunto; assunto sem segmento fica fora do mapa. */
    @Transactional(readOnly = true)
    public Map<Long, Volume> volumes(Collection<Long> assuntoIds) {
        var volumes = new HashMap<Long, Volume>();
        if (assuntoIds.isEmpty()) return volumes;
        for (var linha : segmentoRepository.somarVolumePorAssunto(assuntoIds)) {
            volumes.put((Long) linha[0], new Volume(((Number) linha[1]).longValue(), ((Number) linha[2]).longValue()));
        }
        return volumes;
    }

    @Transactional(readOnly = true)
    public Resumo resumo(Collection<Long> assuntoIds) {
        long prontos = 0, sem = 0, aPreparar = 0;
        for (var estado : estados(assuntoIds).values()) {
            switch (estado.situacao()) {
                case PRONTO -> prontos++;
                case SEM_MATERIAL -> sem++;
                case A_PREPARAR -> aPreparar++;
            }
        }
        return new Resumo(prontos, sem, aPreparar);
    }

    @Transactional(readOnly = true)
    public Optional<LacunaMaterial> lacunaDe(Long assuntoId) {
        return lacunaRepository.findById(assuntoId).filter(l -> l.getDescartadaEm() == null);
    }

    /**
     * D-65 — o planejamento informa que o acervo não ensina o assunto. Regrava
     * se já havia (nova tentativa, motivo novo); nunca apaga.
     */
    @Transactional
    public void registrarLacuna(String chaveExterna, String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new ValidationException("Informe o motivo da lacuna.", "MOTIVO_OBRIGATORIO", "motivo");
        }
        var assunto = assuntoRepository.findByChaveExterna(chaveExterna)
            .orElseThrow(() -> new NotFoundException("Assunto não encontrado.", "ASSUNTO_INEXISTENTE"));
        var agora = Instant.now(clock);
        var lacuna = lacunaRepository.findById(assunto.getId())
            .orElseGet(() -> new LacunaMaterial(assunto.getId(), motivo.strip(), agora));
        lacuna.setMotivo(motivo.strip());
        lacuna.setRegistradaEm(agora);
        lacuna.setDescartadaEm(null); // registrada de novo depois de um "refazer": volta a valer
        lacunaRepository.save(lacuna);
    }

    /**
     * D-66 — refazer o material: arquiva os segmentos ativos e descarta a lacuna dos
     * assuntos, que voltam a "a preparar" e são preparados de novo pelo planejamento.
     * Nada é apagado (D-18); a sessão que estudou um segmento arquivado continua
     * apontando para ele (D-51). docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3B.
     */
    @Transactional
    public void refazer(Collection<Long> assuntoIds) {
        if (assuntoIds.isEmpty()) return;
        var agora = Instant.now(clock);
        segmentoRepository.arquivarDosAssuntos(assuntoIds, agora);
        lacunaRepository.descartarDosAssuntos(assuntoIds, agora);
    }
}
