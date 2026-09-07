package br.com.estudos.segmento;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import br.com.estudos.assunto.Assunto;

/**
 * Pedaço de material de leitura de um assunto (01_DOMINIO §3.7). Nasce só por
 * importação (docs/SPRINT-10-SEGMENTO.md §3.2) — sem endpoint de criação
 * livre pela API, sem coluna `ativo` (D-18 se satisfaz por ausência de
 * caminho de remoção, não por exclusão lógica).
 */
@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Segmento {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assunto_id")
    private Assunto assunto;

    // Sem @Column(nullable = false): a coluna é NULL-ável de verdade (D-53 é
    // CHECK, não NOT NULL nativo — mesmo padrão de Assunto.ordem/D-41).
    // Anotar nullable=false aqui divergiria do schema real sob ddl-auto=validate.
    @Setter
    private String chaveExterna;

    @Setter
    @Column(nullable = false)
    private Integer ordem;

    @Setter
    @Column(nullable = false)
    private String arquivo;

    @Setter
    private Integer paginaInicial;

    @Setter
    private Integer paginaFinal;

    @Setter
    private Integer tempoEstimadoMin;

    @CreationTimestamp
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;
}
