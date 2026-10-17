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

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "relatorios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Relatorio {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "missao_id", nullable = false)
    private Missao missao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tribo_id", nullable = false)
    private Tribo tribo;

    @Column(name = "quantidade_pessoas", nullable = false)
    private int quantidadePessoas;
    @Column(name = "quantidade_novos_afastados", nullable = false)
    private int quantidadeNovosAfastados;
    @Column(name = "observacoes")
    private String observacoes;
    @Column(name = "enviado_em", nullable = false)
    private LocalDateTime enviadoEm;
    @Column(name = "atrasado", nullable = false)
    private boolean atrasado;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "pontos_aprovados", nullable = false)
    private int pontosAprovados;
    @Column(name = "justificativa_revisao")
    private String justificativaRevisao;

    public void aprovar(int pontos, String justificativa) {
        this.status = "APROVADO";
        this.pontosAprovados = pontos;
        this.justificativaRevisao = justificativa;
    }

    public void rejeitar(String justificativa) {
        this.status = "REJEITADO";
        this.justificativaRevisao = justificativa;
    }

}
