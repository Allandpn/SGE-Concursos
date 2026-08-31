package br.com.estudos.assunto;

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
import br.com.estudos.shared.enums.TipoPeso;

@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Assunto {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, length = 250)
    private String nome;

    @Setter
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoPeso peso;

    @Setter
    @Column(nullable = false)
    private Short dificuldadePercebida;

    @Setter
    private Integer ordem;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disciplina_id")
    private Disciplina disciplina;
}
