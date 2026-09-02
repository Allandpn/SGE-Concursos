package br.com.estudos.metrica;

import io.swagger.v3.oas.annotations.media.Schema;

/** As duas variantes de M-3, nunca somadas (00_PRODUTO §7 M-3). */
public record M3Response(
    @Schema(description = "M-3 sobre QUESTOES/FLASHCARDS: desvio entre previsão percentual e resultado") M3ObjetivaResponse objetiva,
    @Schema(description = "M-3 sobre RECUPERACAO: contagem de super/subestimou/acertou") M3SubjetivaResponse subjetiva
) {}
