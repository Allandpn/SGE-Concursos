package br.com.estudos.concurso;

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

import br.com.estudos.assunto.Assunto;
import br.com.estudos.disciplina.Disciplina;
import br.com.estudos.shared.enums.TipoPeso;

/**
 * Uma linha da cobertura do edital (01_DOMINIO §3.8): o que o edital daquele
 * concurso disse de um assunto — peso, disciplina e ordem. É retrato de um
 * documento externo, não estado de estudo; reativar o concurso aplica estes
 * valores de novo (D-60).
 */
@Entity
@Table(name = "concurso_assunto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CoberturaEdital {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concurso_id")
    private Concurso concurso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assunto_id")
    private Assunto assunto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disciplina_id")
    private Disciplina disciplina;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoPeso peso;

    @Column(nullable = false)
    private Integer ordem;

    public CoberturaEdital(Concurso concurso, Assunto assunto, Disciplina disciplina, TipoPeso peso, int ordem) {
        this.concurso = concurso;
        this.assunto = assunto;
        this.disciplina = disciplina;
        this.peso = peso;
        this.ordem = ordem;
    }
}
