package br.com.estudos.importacao;

import java.util.List;

/** Resultado de validar/confirmar (docs/SPRINT-2-CADASTRO.md §5.1). */
public record ResumoImportacao(
    int disciplinasNovas,
    int assuntosNovos,
    int assuntosAtualizados,
    List<LinhaRecusada> recusadas
) {}
