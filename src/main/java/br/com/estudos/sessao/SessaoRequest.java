package br.com.estudos.sessao;

import java.time.LocalDate;
import java.util.UUID;

import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoSessao;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Corpo de POST de sessão (docs/SPRINT-3-SESSAO.md §4).
 * {@code resultado} só é considerado para RECUPERACAO — para QUESTOES/FLASHCARDS
 * o serviço calcula e ignora este campo; para ESTUDO fica sempre null (§3.2).
 */
public record SessaoRequest(
    @Schema(description = "Assunto ao qual a sessão pertence", example = "1")
    Long assuntoId,

    @Schema(description = "Tipo da sessão — define quais campos abaixo se aplicam (D-01/D-02)")
    TipoSessao tipo,

    @Schema(description = "Data em que a sessão ocorreu", example = "2026-06-15")
    LocalDate data,

    @Schema(description = "Duração real da sessão em minutos (D-29 — sempre medida, nunca constante)", example = "20")
    Integer tempoMinutos,

    @Schema(description = "Questões corretas — QUESTOES/FLASHCARDS", example = "9")
    Integer questoesCorretas,

    @Schema(description = "Total de questões — QUESTOES/FLASHCARDS", example = "10")
    Integer questoesTotal,

    @Schema(description = "Formato da banca — obrigatório quando há contagem de questões (D-36)")
    FormatoBanca formato,

    @Schema(description = "Previsão percentual de acerto declarada antes do resultado", example = "70")
    Short previsaoPercentual,

    @Schema(description = "Previsão declarada de reconstrução — só RECUPERACAO")
    ResultadoSessao previsaoReconstrucao,

    @Schema(description = "Resultado declarado — obrigatório em RECUPERACAO, calculado em QUESTOES/FLASHCARDS, ausente em ESTUDO")
    ResultadoSessao resultado,

    @Schema(description = "Identificador da tentativa, gerado no cliente ao abrir a tela (D-45) — reenviar o mesmo id devolve o registro original, nunca duplica")
    UUID tentativaId,

    @Schema(description = "Data sugerida para a próxima sessão deste assunto — livre, não move a escada")
    LocalDate proximaSessaoData,

    @Schema(description = "Descrição livre da próxima sessão sugerida")
    String proximaSessaoDescricao
) {}
