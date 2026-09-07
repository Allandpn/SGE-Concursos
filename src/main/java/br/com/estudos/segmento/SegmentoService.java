package br.com.estudos.segmento;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.estudos.assunto.AssuntoRepository;
import br.com.estudos.shared.exception.ConflictException;
import br.com.estudos.shared.exception.NotFoundException;

/**
 * Segmento nasce só por importação (01_DOMINIO §3.7) — sem endpoint de
 * criação livre pela API. docs/SPRINT-10-SEGMENTO.md §3.2.
 */
@Service
public class SegmentoService {

    private final SegmentoRepository segmentoRepository;
    private final AssuntoRepository assuntoRepository;

    public SegmentoService(SegmentoRepository segmentoRepository, AssuntoRepository assuntoRepository) {
        this.segmentoRepository = segmentoRepository;
        this.assuntoRepository = assuntoRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Segmento> buscarPorChaveExterna(String chaveExterna) {
        return segmentoRepository.findByChaveExterna(chaveExterna);
    }

    /**
     * Cria um segmento novo, ou atualiza um já existente com a mesma
     * {@code chaveExterna} — nunca apaga (D-18). Identidade de reimportação é
     * a chave externa, não {@code (assuntoId, ordem)}: reordenar o material
     * depois de importado não pode confundir um segmento antigo (já
     * referenciado por sessões passadas, D-51) com um novo na mesma posição
     * (docs/SPRINT-10-SEGMENTO.md §0).
     */
    @Transactional
    public Segmento criarOuAtualizar(SegmentoRequest request) {
        var segmento = segmentoRepository.findByChaveExterna(request.chaveExterna())
            .orElseGet(Segmento::new);

        segmento.setAssunto(assuntoRepository.getReferenceById(request.assuntoId()));
        segmento.setChaveExterna(request.chaveExterna());
        segmento.setOrdem(request.ordem());
        segmento.setArquivo(request.arquivo());
        segmento.setPaginaInicial(request.paginaInicial());
        segmento.setPaginaFinal(request.paginaFinal());
        segmento.setTempoEstimadoMin(request.tempoEstimadoMin());

        try {
            return segmentoRepository.saveAndFlush(segmento);
        } catch (DataIntegrityViolationException e) {
            throw traduzirViolacaoDeIntegridade(e);
        }
    }

    /** Lista os segmentos de um assunto, na ordem de leitura. */
    @Transactional(readOnly = true)
    public List<Segmento> listarPorAssunto(Long assuntoId) {
        return segmentoRepository.findByAssuntoIdOrderByOrdemAsc(assuntoId);
    }

    private RuntimeException traduzirViolacaoDeIntegridade(DataIntegrityViolationException e) {
        var nomeRestricao = e.getMostSpecificCause().getMessage();
        if (nomeRestricao == null) throw e;

        if (nomeRestricao.contains("ux_segmento_d50_ordem_por_assunto")) {
            return new ConflictException("Já existe um segmento com esta ordem neste assunto.", "ORDEM_DUPLICADA");
        }
        if (nomeRestricao.contains("fk_segmento_assunto")) {
            return new NotFoundException("Não existe um assunto para esse segmento.", "ASSUNTO_INEXISTENTE");
        }

        throw e;
    }
}
