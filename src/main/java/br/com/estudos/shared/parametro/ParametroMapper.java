package br.com.estudos.shared.parametro;

/** Entidade nunca sai do Service (09_CODE_STYLE §9) — o Controller devolve isto. */
public final class ParametroMapper {

    private ParametroMapper() {}

    public static ParametroResponse toResponse(Parametro parametro) {
        return new ParametroResponse(
            parametro.getChave(), parametro.getValor(), parametro.getDescricao(), parametro.getAtualizadoEm());
    }
}
