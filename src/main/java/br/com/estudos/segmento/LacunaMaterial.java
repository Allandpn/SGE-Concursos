package br.com.estudos.segmento;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * D-65 — o acervo não ensina este assunto, com o motivo dado pelo planejamento
 * (01_DOMINIO §3.10, docs/SPRINT-19-MATERIAL-AUTOMATICO.md §1). Fato registrado,
 * não estado: segmento vence lacuna na derivação, e nada é apagado (D-18).
 */
@Entity
@Table(name = "lacuna_material")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LacunaMaterial {

    @Id
    @Column(name = "assunto_id")
    private Long assuntoId;

    @Setter
    @Column(nullable = false)
    private String motivo;

    @Setter
    @Column(nullable = false)
    private Instant registradaEm;

    public LacunaMaterial(Long assuntoId, String motivo, Instant registradaEm) {
        this.assuntoId = assuntoId;
        this.motivo = motivo;
        this.registradaEm = registradaEm;
    }
}
