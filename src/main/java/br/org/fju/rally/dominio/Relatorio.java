package br.org.fju.rally.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "relatorios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @Convert(converter = ConversorJsonMapa.class)
    @Column(name = "dados", nullable = false, columnDefinition = "TEXT")
    private Map<String, Object> dados;

    @Convert(converter = ConversorJsonListaMapas.class)
    @Column(name = "avisos", nullable = false, columnDefinition = "TEXT")
    private List<Map<String, Object>> avisos;

    public Relatorio(UUID id, Missao missao, Tribo tribo, int quantidadePessoas, int quantidadeNovosAfastados,
                     String observacoes, LocalDateTime enviadoEm, boolean atrasado, String status,
                     int pontosAprovados, String justificativaRevisao, Map<String, Object> dados,
                     List<Map<String, Object>> avisos) {
        this.id = id;
        this.missao = missao;
        this.tribo = tribo;
        this.quantidadePessoas = quantidadePessoas;
        this.quantidadeNovosAfastados = quantidadeNovosAfastados;
        this.observacoes = observacoes;
        this.enviadoEm = enviadoEm;
        this.atrasado = atrasado;
        this.status = status;
        this.pontosAprovados = pontosAprovados;
        this.justificativaRevisao = justificativaRevisao;
        this.dados = dados == null ? Map.of() : Map.copyOf(dados);
        this.avisos = avisos == null ? List.of() : List.copyOf(avisos);
    }

    public Relatorio(UUID id, Missao missao, Tribo tribo, int quantidadePessoas, int quantidadeNovosAfastados,
                     String observacoes, LocalDateTime enviadoEm, boolean atrasado, String status,
                     int pontosAprovados, String justificativaRevisao) {
        this(id, missao, tribo, quantidadePessoas, quantidadeNovosAfastados, observacoes, enviadoEm,
                atrasado, status, pontosAprovados, justificativaRevisao, Map.of(), List.of());
    }

    public void aprovar(int pontos, String justificativa) {
        exigirEnviado();
        this.status = "APROVADO";
        this.pontosAprovados = pontos;
        this.justificativaRevisao = justificativa;
    }

    public void rejeitar(String justificativa) {
        exigirEnviado();
        this.status = "REJEITADO";
        this.justificativaRevisao = justificativa;
    }

    public void ajustarPessoas(int quantidadePessoas, int quantidadeNovosAfastados,
                               List<Map<String, Object>> avisos) {
        exigirEnviado();
        this.quantidadePessoas = quantidadePessoas;
        this.quantidadeNovosAfastados = quantidadeNovosAfastados;
        this.avisos = avisos == null ? List.of() : List.copyOf(avisos);
    }

    private void exigirEnviado() {
        if (!"ENVIADO".equals(status)) {
            throw new IllegalStateException("Somente relatórios enviados podem ser revisados.");
        }
    }

}
