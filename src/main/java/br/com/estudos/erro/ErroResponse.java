package br.com.estudos.erro;

import br.com.estudos.shared.enums.CausaErro;
import br.com.estudos.shared.enums.NivelConfianca;

/** Corpo de resposta de erro (docs/SPRINT-7-METRICAS.md §5). */
public record ErroResponse(
    Long id,
    Long assuntoId,
    Long sessaoId,
    String descricao,
    CausaErro causa,
    NivelConfianca confianca,
    boolean resolvido
) {}
