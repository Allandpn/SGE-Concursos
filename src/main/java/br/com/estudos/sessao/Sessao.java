package br.com.estudos.sessao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
import br.com.estudos.segmento.Segmento;
import br.com.estudos.shared.enums.FormatoBanca;
import br.com.estudos.shared.enums.ResultadoSessao;
import br.com.estudos.shared.enums.TipoSessao;

/** docs/SPRINT-3-SESSAO.md §1.1. Sem PATCH/arquivar — é evento, não cadastro (§4 do doc técnico). */
@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Sessao {

    @Id
    @EqualsAndHashCode.Include
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assunto_id")
    private Assunto assunto;

    // Opcional, só relevante em tipo=ESTUDO (D-51 — banco recusa fora disso
    // e recusa um segmento de outro assunto via FK composta, nunca pré-checado
    // aqui no serviço).
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segmento_id")
    private Segmento segmento;

    @Setter
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TipoSessao tipo;

    @Setter
    @Column(nullable = false)
    private LocalDate data;

    // D-29: duração de bloco não é constante do domínio — o sistema mede a
    // real, toda sessão registra tempo (01_DOMINIO §7.7). Satisfeita por
    // ausência: não existe constante de duração em lugar nenhum do código.
    @Setter
    @Column(nullable = false)
    private Integer tempoMinutos;

    @Setter
    @Enumerated(EnumType.STRING)
    private ResultadoSessao resultado;

    @Setter
    private Integer questoesCorretas;

    @Setter
    private Integer questoesTotal;

    @Setter
    @Enumerated(EnumType.STRING)
    private FormatoBanca formato;

    @Setter
    private Short previsaoPercentual;

    @Setter
    @Enumerated(EnumType.STRING)
    private ResultadoSessao previsaoReconstrucao;

    @Setter
    @Column(nullable = false)
    private UUID tentativaId;

    @Setter
    @Column(nullable = false)
    private boolean ativo = true;

    @CreationTimestamp
    private Instant criadoEm;

    @UpdateTimestamp
    private Instant atualizadoEm;
}
