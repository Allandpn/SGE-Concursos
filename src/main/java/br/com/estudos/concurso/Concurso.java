package br.com.estudos.concurso;

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

import br.com.estudos.shared.enums.OrigemConcurso;

/**
 * O concurso-alvo e os que já foram alvo (01_DOMINIO §3.8, ADR-042). No
 * máximo um ativo — D-58, garantida por ux_concurso_d58_um_ativo, nunca por
 * verificação prévia no serviço.
 */
@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Concurso {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 250)
    private String nome;

    @Setter
    private String cargo;

    @Setter
    private String banca;

    @Setter
    private LocalDate dataProva;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrigemConcurso origem;

    /** D-64 — identificador estável, dado quando o concurso nasce; nunca muda. */
    @Column(nullable = false, updatable = false)
    private String slug;

    @Setter
    @Column(nullable = false)
    private boolean ativo;

    @Setter
    private Instant ativadoEm;

    @Setter
    private Instant desativadoEm;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant criadoEm;

    public Concurso(String nome, OrigemConcurso origem, String slug) {
        this.nome = nome;
        this.origem = origem;
        this.slug = slug;
    }
}
