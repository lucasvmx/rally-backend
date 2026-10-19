package br.org.fju.rally.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "pessoas_pontuadas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PessoaPontuada {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "missao_id", nullable = false)
    private Missao missao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "relatorio_id", nullable = false)
    private Relatorio relatorio;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "nome_normalizado", nullable = false)
    private String nomeNormalizado;

    @Column(name = "ocorrencia", nullable = false)
    private int ocorrencia;

    @Column(name = "categoria", nullable = false)
    private String categoria;

    @Convert(converter = ConversorJsonMapa.class)
    @Column(name = "dados", nullable = false, columnDefinition = "TEXT")
    private Map<String, Object> dados;
}
