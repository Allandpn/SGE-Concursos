package br.com.estudos.turno;

import java.util.List;

import br.com.estudos.assunto.AssuntoResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** O plano do turno de hoje — derivado e efêmero, nunca guardado (01_DOMINIO §7.4). docs/SPRINT-6-TURNO.md §2. */
public record TurnoPlanoResponse(
    @Schema(description = "Fila de recuperações do turno, mais atrasada primeiro, limitada ao teto diário (D-15)")
    List<FilaRecuperacaoItemResponse> filaRecuperacao,

    @Schema(description = "Bloco de conteúdo novo sugerido para o turno — null se a frente estiver cheia")
    AssuntoResponse blocoConteudo
) {}
