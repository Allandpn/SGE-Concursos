package br.com.estudos.prova;

import java.time.Instant;
import java.util.UUID;

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

import br.com.estudos.simulado.Simulado;

/**
 * Uma tentativa de resolver a prova (J-7; 01_DOMINIO §3.11). O {@code tentativaId} é gerado
 * no cliente ao abrir a tela e a mesma tentativa conta uma vez (D-80). Só a tentativa que
 * tem {@code simulado} alimenta M-1, e o índice único parcial do banco garante que é uma
 * por prova (D-78). Não há coluna "acertou": a correção sai do gabarito (D-16).
 */
@Entity
@Table(name = "tentativa_prova")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TentativaProva {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prova_id")
    private Prova prova;

    @Column(nullable = false)
    private UUID tentativaId;

    @Column(nullable = false)
    private Instant iniciadaEm;

    @Setter
    private Instant entregueEm;

    @Column(nullable = false)
    private boolean cronometrada;

    private Integer limiteMinutos;

    /** Preenchido só na tentativa que conta para M-1 (D-78). */
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "simulado_id")
    private Simulado simulado;

    @CreationTimestamp
    private Instant criadoEm;

    public TentativaProva(Prova prova, UUID tentativaId, Instant iniciadaEm, boolean cronometrada, Integer limiteMinutos) {
        this.prova = prova;
        this.tentativaId = tentativaId;
        this.iniciadaEm = iniciadaEm;
        this.cronometrada = cronometrada;
        this.limiteMinutos = limiteMinutos;
    }

    public boolean entregue() {
        return entregueEm != null;
    }
}
