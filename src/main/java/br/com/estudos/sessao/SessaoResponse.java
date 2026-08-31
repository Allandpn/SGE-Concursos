package br.com.estudos.sessao;

import java.time.LocalDate;
import java.util.UUID;

import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoSessao;

/** Corpo de resposta de sessão (docs/SPRINT-3-SESSAO.md §4). */
public record SessaoResponse(
    Long id,
    Long assuntoId,
    TipoSessao tipo,
    LocalDate data,
    Integer tempoMinutos,
    ResultadoSessao resultado,
    Integer questoesCorretas,
    Integer questoesTotal,
    FormatoBanca formato,
    Short previsaoPercentual,
    Boolean previsaoReconstrucao,
    UUID tentativaId,
    LocalDate proximaSessaoData,
    String proximaSessaoDescricao,
    boolean ativo
) {}
