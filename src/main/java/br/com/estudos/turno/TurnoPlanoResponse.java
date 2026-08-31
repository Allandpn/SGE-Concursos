package br.com.estudos.turno;

import java.util.List;

import br.com.estudos.assunto.AssuntoResponse;

/** O plano do turno de hoje — derivado e efêmero, nunca guardado (01_DOMINIO §7.4). docs/SPRINT-6-TURNO.md §2. */
public record TurnoPlanoResponse(
    List<FilaRecuperacaoItemResponse> filaRecuperacao,
    AssuntoResponse blocoConteudo
) {}
