package br.org.fju.rally.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "pontuacoes_manuais")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PontuacaoManual {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rally_id", nullable = false)
    private Rally rally;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nacao_id", nullable = false)
    private Nacao nacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tribo_chamada_id", nullable = false)
    private Tribo triboChamada;

    @Column(name = "atividade", nullable = false, length = 180)
    private String atividade;

    @Column(name = "pontos_por_tribo", nullable = false)
    private int pontosPorTribo;

    @Column(name = "registrado_por", nullable = false, length = 255)
    private String registradoPor;

    @Column(name = "registrada_em", nullable = false)
    private LocalDateTime registradaEm;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "pontuacoes_manuais_destinatarias",
            joinColumns = @JoinColumn(name = "pontuacao_manual_id"),
            inverseJoinColumns = @JoinColumn(name = "tribo_id"))
    private List<Tribo> tribosDestinatarias = new ArrayList<>();

    public PontuacaoManual(UUID id, Rally rally, Nacao nacao, Tribo triboChamada,
                           String atividade, int pontosPorTribo, String registradoPor,
                           LocalDateTime registradaEm, List<Tribo> tribosDestinatarias) {
        this.id = id;
        this.rally = rally;
        this.nacao = nacao;
        this.triboChamada = triboChamada;
        this.atividade = atividade;
        this.pontosPorTribo = pontosPorTribo;
        this.registradoPor = registradoPor;
        this.registradaEm = registradaEm;
        this.tribosDestinatarias.addAll(tribosDestinatarias);
    }
}
