package br.com.estudos.erro;

/** Entidade nunca sai do Service (09_CODE_STYLE §9) — o Controller devolve isto. */
public final class ErroMapper {

    private ErroMapper() {}

    public static ErroResponse toResponse(Erro erro) {
        return new ErroResponse(
            erro.getId(),
            erro.getAssunto().getId(),
            erro.getSessao() != null ? erro.getSessao().getId() : null,
            erro.getDescricao(),
            erro.getCausa(),
            erro.getConfianca(),
            erro.isResolvido()
        );
    }
}
