package br.com.estudos.simulado;

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

import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.shared.enums.FormatoBanca;

/**
 * docs/SPRINT-8-SIMULADO.md §1.2. Aponta para disciplina, nunca para
 * assunto — D-13, garantida pela ausência da coluna (03_INVARIANTES §9,
 * mesmo mecanismo estrutural de D-16).
 */
@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ResultadoSimulado {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulado_id")
    private Simulado simulado;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disciplina_id")
    private Disciplina disciplina;

    @Setter
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FormatoBanca formato;

    @Setter
    @Column(nullable = false)
    private Integer questoesCorretas;

    @Setter
    @Column(nullable = false)
    private Integer questoesTotal;

    @CreationTimestamp
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;
}
