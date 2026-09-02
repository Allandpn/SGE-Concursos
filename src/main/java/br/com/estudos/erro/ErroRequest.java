package br.com.estudos.erro;

import br.com.estudos.shared.enums.CausaErro;
import br.com.estudos.shared.enums.NivelConfianca;
import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de POST de erro (docs/SPRINT-7-METRICAS.md §5). */
public record ErroRequest(
    @Schema(description = "Assunto ao qual o erro aponta — precisa existir (D-46)", example = "1")
    Long assuntoId,

    @Schema(description = "Sessão em que o erro apareceu — opcional, mas se presente precisa existir (D-46)")
    Long sessaoId,

    @Schema(description = "Descrição livre do erro cometido")
    String descricao,

    @Schema(description = "Causa raiz apontada pelo usuário")
    CausaErro causa,

    @Schema(description = "Confiança do usuário na causa apontada")
    NivelConfianca confianca
) {}
