package br.com.estudos.simulado;

import br.com.estudos.shared.enums.FormatoBanca;

public record ResultadoSimuladoRequest(
    Long disciplinaId,
    FormatoBanca formato,
    Integer questoesCorretas,
    Integer questoesTotal
) {}
