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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import br.com.estudos.concurso.Concurso;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.SituacaoGabarito;
import br.com.estudos.shared.enums.StatusProva;

/**
 * Uma prova objetiva hospedada (01_DOMINIO §3.11, ADR-046): processo com estados,
 * como a análise de edital. Serve só à verificação do material e à resolução (D-72);
 * é sempre avaliada contra o concurso-alvo do momento do envio (D-77).
 */
@Entity
@Table
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Prova {

    @Id
    @EqualsAndHashCode.Include
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concurso_id")
    private Concurso concurso;

    @Column(nullable = false, length = 250)
    private String nome;

    private String banca;

    private Short ano;

    private String tipo;

    private String cargoProva;

    /** Nulo até a leitura, que é quem descobre. */
    @Enumerated(EnumType.STRING)
    private FormatoBanca formato;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusProva status;

    @Enumerated(EnumType.STRING)
    private StatusProva etapaFalha;

    @Column(columnDefinition = "TEXT")
    private String erro;

    @Column(nullable = false, length = 64)
    private String hashProva;

    /** Arquivo do gabarito em entrada/&lt;id&gt;/; nulo = nenhum enviado ainda. */
    private String gabaritoArquivo;

    /** Nulo = sem gabarito (D-81). */
    @Enumerated(EnumType.STRING)
    private SituacaoGabarito gabaritoSituacao;

    @Column(columnDefinition = "TEXT")
    private String gabaritoRecorte;

    private Integer questoesEsperadas;

    private String modelo;

    @CreationTimestamp
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;

    public Prova(Concurso concurso, String nome, String hashProva, String tipo, String gabaritoArquivo) {
        this.concurso = concurso;
        this.nome = nome;
        this.hashProva = hashProva;
        this.tipo = tipo;
        this.gabaritoArquivo = gabaritoArquivo;
        this.status = StatusProva.LENDO;
    }
}
