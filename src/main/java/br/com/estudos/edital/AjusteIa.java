package br.com.estudos.edital;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chave e modelo de IA cadastrados em Ajustes (ADR-044,
 * docs/SPRINT-19-MATERIAL-AUTOMATICO.md §3). Uma linha só (id = 1, garantido
 * por ck_configuracao_ia_linha_unica); coluna nula = vale o ambiente.
 */
@Entity
@Table(name = "configuracao_ia")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AjusteIa {

    public static final short UNICA = 1;

    @Id
    private Short id;

    private String chave;

    private String modelo;

    private Instant atualizadoEm;

    public AjusteIa(Instant agora) {
        this.id = UNICA;
        this.atualizadoEm = agora;
    }
}
