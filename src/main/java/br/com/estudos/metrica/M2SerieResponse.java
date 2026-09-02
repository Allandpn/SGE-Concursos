package br.com.estudos.metrica;

import br.com.estudos.shared.enums.TipoSessao;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Uma série de retenção por tipo de sessão — nunca somada com as outras
 * (docs/SPRINT-7-METRICAS.md §0, mesmo princípio de D-36). `percentualPrimeira`
 * é a primeira exposição; `percentualMediaSeguintes` vem nulo se ainda não
 * houve revisão depois da primeira.
 */
public record M2SerieResponse(
    @Schema(description = "Tipo de sessão desta série") TipoSessao tipo,
    @Schema(description = "Percentual de acerto na primeira exposição") Double percentualPrimeira,
    @Schema(description = "Média de percentual nas exposições seguintes — null se ainda não houve nenhuma") Double percentualMediaSeguintes,
    @Schema(description = "Total de observações nesta série") int totalObservacoes
) {}
