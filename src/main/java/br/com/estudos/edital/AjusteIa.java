package br.com.estudos.edital;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * IA cadastrada em Ajustes (ADR-044, ADR-045; docs/SPRINT-19-MATERIAL-AUTOMATICO.md
 * §3 e §3A). Uma linha só (id = 1, garantido por ck_configuracao_ia_linha_unica);
 * coluna nula = vale o padrão (ambiente para o OpenRouter, Sonnet para o Claude).
 */
@Entity
@Table(name = "configuracao_ia")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AjusteIa {

    public static final short UNICA = 1;

    @Id
    private Short id;

    /** OpenRouter. */
    private String chave;

    /** OpenRouter. */
    private String modelo;

    @Enumerated(EnumType.STRING)
    private ProvedorIa provedor;

    private String claudeToken;

    @Column(name = "claude_modelo")
    private String claudeModelo;

    private String claudeFalhaMotivo;

    private Instant claudeFalhaEm;

    private Instant atualizadoEm;

    public AjusteIa(Instant agora) {
        this.id = UNICA;
        this.atualizadoEm = agora;
    }
}
