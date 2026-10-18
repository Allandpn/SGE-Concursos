package br.com.estudos.prova;

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

/** Uma alternativa (A–E) de uma questão de múltipla escolha. Certo/errado não tem. */
@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Alternativa {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questao_id")
    private Questao questao;

    @Column(nullable = false, length = 1)
    private String letra;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    Alternativa(Questao questao, String letra, String texto) {
        this.questao = questao;
        this.letra = letra;
        this.texto = texto;
    }
}
