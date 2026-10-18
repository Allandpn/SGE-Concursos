package br.com.estudos.prova;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import br.com.estudos.segmento.Segmento;
import br.com.estudos.shared.enums.VereditoCobertura;

/**
 * O julgamento de uma questão contra o material do assunto (01_DOMINIO §3.11). Fato
 * registrado, nunca alterado (D-73): sem setters, e o gatilho do banco recusa UPDATE
 * e DELETE. Reanalisar insere um veredito novo; o vigente é o de maior id. A data é
 * a do banco — retrato do material daquele dia (D-76).
 */
@Entity
@Table(name = "veredito_questao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class VereditoQuestao {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questao_id", updatable = false)
    private Questao questao;

    @Column(nullable = false, updatable = false)
    @Enumerated(EnumType.STRING)
    private VereditoCobertura veredito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segmento_id", updatable = false)
    private Segmento segmento;

    @Column(updatable = false)
    private Integer paginaSegmento;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String falta;

    @Column(nullable = false, updatable = false, length = 1)
    private String gabaritoConsiderado;

    @Column(updatable = false)
    private String modelo;

    @Column(insertable = false, updatable = false)
    private Instant criadoEm;

    public VereditoQuestao(Questao questao, VereditoCobertura veredito, Segmento segmento,
                           Integer paginaSegmento, String falta, String modelo) {
        this.questao = questao;
        this.veredito = veredito;
        this.segmento = segmento;
        this.paginaSegmento = paginaSegmento;
        this.falta = falta;
        this.gabaritoConsiderado = questao.getRespostaGabarito();
        this.modelo = modelo;
    }
}
