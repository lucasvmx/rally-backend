package br.org.fju.rally.dominio;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "integrantes_tribo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntegranteTribo {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tribo_id", nullable = false) private Tribo tribo;
    @Column(nullable = false, length = 160) private String nome;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Titulo titulo;
    @Enumerated(EnumType.STRING) @Column(length = 30) private Papel papel;
    public enum Titulo { INTEGRANTE, CPO, COLABORADOR, OBREIRO }
    public enum Papel { COORDENADOR_TRIBO, AUXILIAR_TRIBO }
    public IntegranteTribo(UUID id, Tribo tribo, String nome, Titulo titulo, Papel papel) {
        this.id = id; this.tribo = tribo; atualizar(nome, titulo, papel);
    }
    public void atualizar(String nome, Titulo titulo, Papel papel) {
        this.nome = nome.strip(); this.titulo = titulo; this.papel = papel;
    }
}
