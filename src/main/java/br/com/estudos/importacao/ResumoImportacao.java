package br.com.estudos.importacao;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resultado de validar/confirmar (docs/SPRINT-2-CADASTRO.md §5.1). */
public record ResumoImportacao(
    @Schema(description = "Quantas disciplinas novas o arquivo criaria/criou") int disciplinasNovas,
    @Schema(description = "Quantos assuntos novos o arquivo criaria/criou") int assuntosNovos,
    @Schema(description = "Quantos assuntos existentes o arquivo atualizaria/atualizou") int assuntosAtualizados,
    @Schema(description = "Linhas recusadas e o motivo — se não vazio em /validar, /confirmar recusa o arquivo inteiro (regra 1)") List<LinhaRecusada> recusadas
) {}
