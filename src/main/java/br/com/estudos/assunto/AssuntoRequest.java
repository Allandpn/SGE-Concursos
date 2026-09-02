package br.com.estudos.assunto;

import br.com.estudos.shared.enums.TipoPeso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo de POST/PATCH de assunto (docs/SPRINT-2-CADASTRO.md §4). As
 * anotações de Bean Validation (ADR-035) só valem no POST —
 * {@code AssuntoController.atualizar} não usa {@code @Valid}, porque no
 * PATCH campo ausente é "não mude isto", não erro.
 */
public record AssuntoRequest(
    @NotNull(message = "DISCIPLINA_OBRIGATORIA")
    @Schema(description = "Disciplina à qual o assunto pertence", example = "1")
    Long disciplinaId,

    @NotBlank(message = "NOME_OBRIGATORIO")
    @Schema(description = "Nome do assunto, único dentro da disciplina (J-1)", example = "Normalização e Dependências Funcionais")
    String nome,

    @NotNull(message = "PESO_OBRIGATORIO")
    @Schema(description = "Peso do assunto no edital")
    TipoPeso peso,

    @NotNull(message = "DIFICULDADE_PERCEBIDA_OBRIGATORIA")
    @Min(value = 1, message = "DIFICULDADE_PERCEBIDA_INVALIDA")
    @Max(value = 5, message = "DIFICULDADE_PERCEBIDA_INVALIDA")
    @Schema(description = "Dificuldade percebida pelo usuário, de 1 (fácil) a 5 (difícil)", example = "3")
    Short dificuldadePercebida,

    @NotNull(message = "ORDEM_OBRIGATORIA")
    @Schema(description = "Prioridade no backlog da disciplina — menor ordem entra primeiro na frente (D-41). Única entre ativos (D-48)", example = "1")
    Integer ordem
) {}
