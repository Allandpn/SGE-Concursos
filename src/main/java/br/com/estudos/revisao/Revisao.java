package br.com.estudos.revisao;

import java.time.Instant;
import java.time.LocalDate;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import br.com.estudos.assunto.Assunto;
import br.com.estudos.sessao.Sessao;
import br.com.estudos.shared.enums.SituacaoRevisao;

/** docs/SPRINT-4-ESCADA.md §2.1. Nasce só como efeito de registrar sessão (§3) — nenhum endpoint cria/edita direto. */
@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Revisao {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assunto_id")
    private Assunto assunto;

    @Setter
    @Column(nullable = false)
    private Integer nivel;

    @Setter
    @Column(nullable = false)
    private LocalDate dataPrevista;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_origem_id")
    private Sessao sessaoOrigem;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sessao_cumpriu_id")
    private Sessao sessaoCumpriu;

    @Setter
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SituacaoRevisao situacao;

    // ADR-032: protege contra tentativas diferentes colidindo na mesma
    // pendente (D-45/ADR-031 já cobre a mesma tentativa reenviada).
    @Version
    @Column(nullable = false)
    private Long versao;

    @CreationTimestamp
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;
}
