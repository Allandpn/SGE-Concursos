package br.com.estudos.erro;

import br.com.estudos.shared.enums.CausaErro;
import br.com.estudos.shared.enums.NivelConfianca;
import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de resposta de erro (docs/SPRINT-7-METRICAS.md §5). */
public record ErroResponse(
    @Schema(description = "Identificador do erro") Long id,
    @Schema(description = "Assunto ao qual o erro aponta") Long assuntoId,
    @Schema(description = "Sessão em que o erro apareceu, se houver") Long sessaoId,
    @Schema(description = "Descrição livre do erro cometido") String descricao,
    @Schema(description = "Causa raiz apontada pelo usuário") CausaErro causa,
    @Schema(description = "Confiança do usuário na causa apontada") NivelConfianca confianca,
    @Schema(description = "Se o usuário já marcou este erro como resolvido") boolean resolvido
) {}
