package br.com.estudos.simulado;

import java.time.LocalDate;
import java.util.List;

public record SimuladoResponse(
    Long id,
    LocalDate data,
    Integer duracaoMinutos,
    List<ResultadoSimuladoResponse> resultados
) {}
