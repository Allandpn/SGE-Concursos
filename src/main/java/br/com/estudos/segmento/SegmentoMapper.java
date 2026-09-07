package br.com.estudos.segmento;

/** Entidade nunca sai do Service (09_CODE_STYLE §9) — o Controller devolve isto. */
public final class SegmentoMapper {

    private SegmentoMapper() {}

    public static SegmentoResponse toResponse(Segmento segmento) {
        return new SegmentoResponse(
            segmento.getId(),
            segmento.getAssunto().getId(),
            segmento.getChaveExterna(),
            segmento.getOrdem(),
            segmento.getArquivo(),
            segmento.getPaginaInicial(),
            segmento.getPaginaFinal(),
            segmento.getTempoEstimadoMin()
        );
    }
}
