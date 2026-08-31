package br.com.estudos.shared.parametro;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/** "Sétimo conceito" de 01_DOMINIO §3: configuração, não domínio — mesmo assim mapeada, para o serviço ler o valor em vez de embutir constante. */
@Entity
@Table
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Parametro {

    @Id
    @EqualsAndHashCode.Include
    @Column(length = 100)
    private String chave;

    @Column(nullable = false)
    private String valor;

    @Column(nullable = false)
    private String descricao;

    @UpdateTimestamp
    private Instant atualizadoEm;
}
