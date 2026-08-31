package br.com.estudos.simulado;

import br.com.estudos.shared.enums.FormatoBanca;

public record ResultadoSimuladoResponse(
    Long disciplinaId,
    FormatoBanca formato,
    int questoesCorretas,
    int questoesTotal
) {}
