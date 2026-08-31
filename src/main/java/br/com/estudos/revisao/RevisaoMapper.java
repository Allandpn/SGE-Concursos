package br.com.estudos.revisao;

/** Entidade nunca sai do Service (09_CODE_STYLE §9) — o Controller devolve isto. */
public final class RevisaoMapper {

    private RevisaoMapper() {}

    public static RevisaoResponse toResponse(Revisao revisao) {
        return new RevisaoResponse(
            revisao.getId(),
            revisao.getAssunto().getId(),
            revisao.getNivel(),
            revisao.getDataPrevista(),
            revisao.getSessaoOrigem() == null ? null : revisao.getSessaoOrigem().getId(),
            revisao.getSessaoCumpriu() == null ? null : revisao.getSessaoCumpriu().getId(),
            revisao.getSituacao()
        );
    }
}
