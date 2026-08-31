package br.com.estudos.erro;

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

import br.com.estudos.assunto.Assunto;
import br.com.estudos.sessao.Sessao;
import br.com.estudos.shared.enums.CausaErro;
import br.com.estudos.shared.enums.NivelConfianca;

/**
 * docs/SPRINT-1-BANCO.md §2.5 (tabela, desde V1) e docs/SPRINT-7-METRICAS.md
 * §1 (camada JPA, nova). Pertence a um assunto (D-46); opcionalmente a uma
 * sessão (D-46). Sem endpoint de resolver nesta sprint — nasce aberto.
 */
@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Erro {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assunto_id")
    private Assunto assunto;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_id")
    private Sessao sessao;

    @Setter
    @Column(nullable = false)
    private String descricao;

    @Setter
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CausaErro causa;

    @Setter
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NivelConfianca confianca;

    @Setter
    @Column(nullable = false)
    private boolean resolvido = false;

    @CreationTimestamp
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;
}
