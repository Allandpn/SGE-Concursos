package br.com.estudos.metrica;

import java.util.List;

public record M1Response(JanelaMetrica janela, List<M1LinhaResponse> linhas) {}
