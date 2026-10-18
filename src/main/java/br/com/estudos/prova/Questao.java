package br.com.estudos.prova;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.shared.enums.AtribuicaoQuestao;
import br.com.estudos.shared.enums.MotivoNaoAvaliavel;

/**
 * Uma questão de uma prova hospedada (01_DOMINIO §3.11). Só existe dentro do contexto
 * da prova (D-72). Avaliável = sem {@code motivoNaoAvaliavel}; as demais saem do
 * denominador e são declaradas (D-70).
 */
@Entity
@Table
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Questao {

    @Id
    @EqualsAndHashCode.Include
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prova_id")
    private Prova prova;

    @Column(nullable = false)
    private Integer numero;

    @Column(columnDefinition = "TEXT")
    private String enunciado;

    /** O texto ou comando a que o item do CEBRASPE está ligado. */
    @Column(columnDefinition = "TEXT")
    private String textoBase;

    private String cabecalhoDisciplina;

    /** A–E; certo/errado usa C e E. Nula se anulada ou sem gabarito. */
    private String respostaGabarito;

    @Enumerated(EnumType.STRING)
    private MotivoNaoAvaliavel motivoNaoAvaliavel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assunto_id")
    private Assunto assunto;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AtribuicaoQuestao atribuicao = AtribuicaoQuestao.PENDENTE;

    @Column(nullable = false)
    private boolean lidaPorOcr;

    private Integer paginaOrigem;

    @OneToMany(mappedBy = "questao", cascade = CascadeType.ALL)
    @OrderBy("letra")
    private List<Alternativa> alternativas = new ArrayList<>();

    @CreationTimestamp
    private Instant criadoEm;

    public Questao(Prova prova, Integer numero) {
        this.prova = prova;
        this.numero = numero;
    }

    public boolean avaliavel() {
        return motivoNaoAvaliavel == null;
    }

    public void adicionar(String letra, String texto) {
        alternativas.add(new Alternativa(this, letra, texto));
    }
}
