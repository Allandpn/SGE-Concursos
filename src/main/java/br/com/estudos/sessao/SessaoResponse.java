package br.com.estudos.sessao;

import java.time.LocalDate;
import java.util.UUID;

import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoSessao;
import io.swagger.v3.oas.annotations.media.Schema;

/** Corpo de resposta de sessão (docs/SPRINT-3-SESSAO.md §4). */
public record SessaoResponse(
    @Schema(description = "Identificador da sessão") Long id,
    @Schema(description = "Assunto ao qual a sessão pertence") Long assuntoId,
    @Schema(description = "Tipo da sessão") TipoSessao tipo,
    @Schema(description = "Data em que a sessão ocorreu") LocalDate data,
    @Schema(description = "Duração real em minutos") Integer tempoMinutos,
    @Schema(description = "Resultado — declarado (RECUPERACAO) ou calculado (QUESTOES/FLASHCARDS); null em ESTUDO (D-02)") ResultadoSessao resultado,
    @Schema(description = "Questões corretas") Integer questoesCorretas,
    @Schema(description = "Total de questões") Integer questoesTotal,
    @Schema(description = "Formato da banca") FormatoBanca formato,
    @Schema(description = "Previsão percentual declarada antes do resultado") Short previsaoPercentual,
    @Schema(description = "Previsão declarada de reconstrução — só RECUPERACAO") ResultadoSessao previsaoReconstrucao,
    @Schema(description = "Identificador da tentativa (D-45) — igual entre o envio original e um reenvio") UUID tentativaId,
    @Schema(description = "Sessão nunca é arquivada — sempre true (é evento, não cadastro)") boolean ativo
) {}
