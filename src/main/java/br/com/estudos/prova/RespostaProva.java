package br.com.estudos.prova;

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

/** A resposta do usuário a uma questão numa tentativa; nula = só marcada para revisar. */
@Entity
@Table(name = "resposta_prova")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RespostaProva {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tentativa_id")
    private TentativaProva tentativa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questao_id")
    private Questao questao;

    @Setter
    @Column(length = 1)
    private String alternativa;

    @Setter
    @Column(nullable = false)
    private boolean marcadaRevisar;

    @Setter
    @Column(nullable = false)
    private Instant respondidaEm;

    public RespostaProva(TentativaProva tentativa, Questao questao) {
        this.tentativa = tentativa;
        this.questao = questao;
    }
}
