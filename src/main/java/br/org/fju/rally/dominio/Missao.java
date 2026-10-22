package br.org.fju.rally.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "missoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Missao {
    @Id
    private UUID id;
    @Column(name = "nome", nullable = false)
    private String nome;
    @Column(name = "descricao")
    private String descricao;
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;
    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;
    @Column(name = "prazo_envio")
    private Instant prazoEnvio;
    @Column(name = "pontos_por_pessoa", nullable = false)
    private int pontosPorPessoa;
    @Column(name = "pontos_por_novo_afastado", nullable = false)
    private int pontosPorNovoAfastado;
    @Column(name = "bonificacao_a_cada_cinco_novos", nullable = false)
    private int bonificacaoACadaCincoNovos;
    @Column(name = "meta_nacional", nullable = false)
    private int metaNacional;
    @Column(name = "bonificacao_meta_nacional", nullable = false)
    private int bonificacaoMetaNacional;
    @Column(name = "exige_foto", nullable = false)
    private boolean exigeFoto;
    @Column(name = "tipo_pessoa", nullable = false)
    private String tipoPessoa;
    @Column(name = "link_google_forms")
    private String linkGoogleForms;
    @Column(name = "status", nullable = false)
    private String status;
    @Column(name = "versao", nullable = false)
    private int versao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rally_id", nullable = false)
    private Rally rally;

    @Convert(converter = ConversorJsonListaMapas.class)
    @Column(name = "regras_pontuacao", nullable = false, columnDefinition = "TEXT")
    private List<Map<String, Object>> regrasPontuacao;

    @Convert(converter = ConversorJsonListaMapas.class)
    @Column(name = "campos_relatorio", nullable = false, columnDefinition = "TEXT")
    private List<Map<String, Object>> camposRelatorio;

    public Missao(UUID id, String nome, String descricao, LocalDate dataInicio, LocalDate dataFim,
                  Instant prazoEnvio, int pontosPorPessoa, int pontosPorNovoAfastado,
                  int bonificacaoACadaCincoNovos, int metaNacional, int bonificacaoMetaNacional,
                  boolean exigeFoto, String tipoPessoa, String linkGoogleForms, String status, int versao,
                  Rally rally, List<Map<String, Object>> regrasPontuacao,
                  List<Map<String, Object>> camposRelatorio) {
        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.prazoEnvio = prazoEnvio;
        this.pontosPorPessoa = pontosPorPessoa;
        this.pontosPorNovoAfastado = pontosPorNovoAfastado;
        this.bonificacaoACadaCincoNovos = bonificacaoACadaCincoNovos;
        this.metaNacional = metaNacional;
        this.bonificacaoMetaNacional = bonificacaoMetaNacional;
        this.exigeFoto = exigeFoto;
        this.tipoPessoa = tipoPessoa;
        this.linkGoogleForms = linkGoogleForms;
        this.status = status;
        this.versao = versao;
        this.rally = rally;
        this.regrasPontuacao = regrasPontuacao == null ? List.of() : List.copyOf(regrasPontuacao);
        this.camposRelatorio = camposRelatorio == null ? List.of() : List.copyOf(camposRelatorio);
    }

    public Missao(UUID id, String nome, String descricao, LocalDate dataInicio, LocalDate dataFim,
                  Instant prazoEnvio, int pontosPorPessoa, int pontosPorNovoAfastado,
                  int bonificacaoACadaCincoNovos, int metaNacional, int bonificacaoMetaNacional,
                  boolean exigeFoto, String tipoPessoa, String status, int versao) {
        this(id, nome, descricao, dataInicio, dataFim, prazoEnvio, pontosPorPessoa, pontosPorNovoAfastado,
                bonificacaoACadaCincoNovos, metaNacional, bonificacaoMetaNacional, exigeFoto, tipoPessoa,
                null, status, versao, null, List.of(), List.of());
    }

}
