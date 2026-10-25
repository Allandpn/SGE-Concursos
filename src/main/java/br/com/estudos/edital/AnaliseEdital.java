package br.com.estudos.edital;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import br.com.estudos.shared.enums.StatusAnaliseEdital;

/**
 * Uma análise de edital (01_DOMINIO §3.9): processo, não estado de estudo.
 * Guarda o texto extraído para repetir a leitura do cabeçalho sem reenviar o PDF; a leitura do
 * planejamento fica nas tabelas da conferência (V17) e só vira cadastro na conclusão (D-62, D-92).
 */
@Entity
@Table
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AnaliseEdital {

    @Id
    @EqualsAndHashCode.Include
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 250)
    private String nomeArquivo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatusAnaliseEdital status;

    /** Etapa que falhou: LENDO_EDITAL, SEPARANDO_ASSUNTOS ou uma das etapas do planejamento (EXTRACAO, ORGANIZACAO, QUEBRA, LIGACAO). */
    private String etapaFalha;

    @Column(columnDefinition = "TEXT")
    private String erro;

    private String modelo;
    private String concursoNome;
    private String banca;
    private LocalDate dataProva;

    /** JSON: lista de cargos da etapa 1. */
    @Column(columnDefinition = "TEXT")
    private String cargos;

    private String cargoEscolhido;

    /** Caminho do PDF no volume {@code editais}, enquanto a conferência está aberta. */
    private String pdfArquivo;

    private Long concursoId;

    @Setter(AccessLevel.NONE)
    @CreationTimestamp
    @Column(updatable = false)
    private Instant criadoEm;

    @Setter(AccessLevel.NONE)
    @UpdateTimestamp
    private Instant atualizadoEm;

    public AnaliseEdital(String nomeArquivo, String texto, String modelo) {
        this.nomeArquivo = nomeArquivo;
        this.texto = texto;
        this.modelo = modelo;
        this.status = StatusAnaliseEdital.LENDO_EDITAL;
    }
}
