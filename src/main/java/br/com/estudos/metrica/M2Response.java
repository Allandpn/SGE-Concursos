package br.com.estudos.metrica;

import java.util.List;

public record M2Response(Long assuntoId, List<M2SerieResponse> series) {}
