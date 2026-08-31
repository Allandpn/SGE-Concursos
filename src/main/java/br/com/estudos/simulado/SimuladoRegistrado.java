package br.com.estudos.simulado;

import java.util.List;

/** Retorno de {@link SimuladoService#registrar} — Simulado não tem coleção de resultados (§1). */
public record SimuladoRegistrado(Simulado simulado, List<ResultadoSimulado> resultados) {}
