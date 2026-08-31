package br.com.estudos.assunto;

/** Entidade nunca sai do Service (09_CODE_STYLE §9) — o Controller devolve isto. */
public final class AssuntoMapper {

    private AssuntoMapper() {}

    public static AssuntoResponse toResponse(Assunto assunto) {
        return new AssuntoResponse(
            assunto.getId(),
            assunto.getDisciplina().getId(),
            assunto.getNome(),
            assunto.getPeso(),
            assunto.getDificuldadePercebida(),
            assunto.getOrdem(),
            assunto.isAtivo()
        );
    }
}
