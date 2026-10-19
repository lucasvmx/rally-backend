package br.org.fju.rally.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "tribos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Tribo {
    @Id
    private UUID id;
    @Column(name = "nome", nullable = false)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nacao_id", nullable = false)
    private Nacao nacao;

    @Column(name = "ativa", nullable = false)
    private boolean ativa;

    public void definirAtiva(boolean ativa) {
        this.ativa = ativa;
    }
}
