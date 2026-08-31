package br.com.estudos.disciplina;

/** Entidade nunca sai do Service (09_CODE_STYLE §9) — o Controller devolve isto. */
public final class DisciplinaMapper {

    private DisciplinaMapper() {}

    public static DisciplinaResponse toResponse(Disciplina disciplina) {
        return new DisciplinaResponse(disciplina.getId(), disciplina.getNome(), disciplina.getPeso(), disciplina.isAtivo());
    }
}
