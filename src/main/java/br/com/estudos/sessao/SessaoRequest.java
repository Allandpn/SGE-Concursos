package br.com.estudos.sessao;

import java.time.LocalDate;
import java.util.UUID;

import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoSessao;

/**
 * Corpo de POST de sessão (docs/SPRINT-3-SESSAO.md §4).
 * {@code resultado} só é considerado para RECUPERACAO — para QUESTOES/FLASHCARDS
 * o serviço calcula e ignora este campo; para ESTUDO fica sempre null (§3.2).
 */
public record SessaoRequest(
    Long assuntoId,
    TipoSessao tipo,
    LocalDate data,
    Integer tempoMinutos,
    Integer questoesCorretas,
    Integer questoesTotal,
    FormatoBanca formato,
    Short previsaoPercentual,
    Boolean previsaoReconstrucao,
    ResultadoSessao resultado,
    UUID tentativaId,
    LocalDate proximaSessaoData,
    String proximaSessaoDescricao
) {}
