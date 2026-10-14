package br.com.estudos.frente;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.segmento.Segmento;

/**
 * O bloco de conteúdo do turno: o assunto, o segmento a ler (null se o assunto
 * não tem segmentos) e se é continuação de um assunto em estudo (D-55,
 * docs/SPRINT-17-USO-REAL.md §1.2).
 */
public record SugestaoConteudo(Assunto assunto, Segmento segmento, int totalSegmentos, boolean continuacao) {}
