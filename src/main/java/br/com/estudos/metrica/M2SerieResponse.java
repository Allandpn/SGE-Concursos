package br.com.estudos.metrica;

import br.com.estudos.shared.enums.TipoSessao;

/**
 * Uma série de retenção por tipo de sessão — nunca somada com as outras
 * (docs/SPRINT-7-METRICAS.md §0, mesmo princípio de D-36). `percentualPrimeira`
 * é a primeira exposição; `percentualMediaSeguintes` vem nulo se ainda não
 * houve revisão depois da primeira.
 */
public record M2SerieResponse(
    TipoSessao tipo,
    Double percentualPrimeira,
    Double percentualMediaSeguintes,
    int totalObservacoes
) {}
