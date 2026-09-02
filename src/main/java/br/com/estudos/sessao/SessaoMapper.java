package br.com.estudos.sessao;

/** Entidade nunca sai do Service (09_CODE_STYLE §9) — o Controller devolve isto. */
public final class SessaoMapper {

    private SessaoMapper() {}

    public static SessaoResponse toResponse(Sessao sessao) {
        return new SessaoResponse(
            sessao.getId(),
            sessao.getAssunto().getId(),
            sessao.getTipo(),
            sessao.getData(),
            sessao.getTempoMinutos(),
            sessao.getResultado(),
            sessao.getQuestoesCorretas(),
            sessao.getQuestoesTotal(),
            sessao.getFormato(),
            sessao.getPrevisaoPercentual(),
            sessao.getPrevisaoReconstrucao(),
            sessao.getTentativaId(),
            sessao.isAtivo()
        );
    }
}
