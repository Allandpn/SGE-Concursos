package br.com.estudos.turno;

import java.util.List;

import br.com.estudos.assunto.AssuntoResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** O plano do turno de hoje — derivado e efêmero, nunca guardado (01_DOMINIO §7.4). docs/SPRINT-6-TURNO.md §2. */
public record TurnoPlanoResponse(
    @Schema(description = "Fila de recuperações do turno, mais atrasada primeiro, limitada ao teto diário (D-15)")
    List<FilaRecuperacaoItemResponse> filaRecuperacao,

    @Schema(description = "Assuntos que FALHA devolveu à fila de estudo, falha mais antiga primeiro — sem teto, não ocupam vaga nova (D-07/D-11)")
    List<ReestudoItemResponse> reestudos,

    @Schema(description = "Assunto do bloco de conteúdo do turno — null se não houver leitura a continuar nem vaga na frente")
    AssuntoResponse blocoConteudo,

    @Schema(description = "true quando o bloco continua a leitura de um assunto em estudo (D-55)")
    boolean blocoContinuacao,

    @Schema(description = "Segmento a ler no bloco — null se o assunto não tem segmentos (D-55)")
    SegmentoSugeridoResponse segmentoSugerido,

    @Schema(description = "Represamento acima do limiar: o plano não sugere assunto novo, só continuação (D-56, 01_DOMINIO §5.3)")
    boolean alertaRepresamento
) {}
