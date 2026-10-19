package br.org.fju.rally.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "missoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
    private LocalDateTime prazoEnvio;
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

    public Missao(UUID id, String nome, String descricao, LocalDate dataInicio, LocalDate dataFim,
                  LocalDateTime prazoEnvio, int pontosPorPessoa, int pontosPorNovoAfastado,
                  int bonificacaoACadaCincoNovos, int metaNacional, int bonificacaoMetaNacional,
                  boolean exigeFoto, String tipoPessoa, String status, int versao) {
        this(id, nome, descricao, dataInicio, dataFim, prazoEnvio, pontosPorPessoa, pontosPorNovoAfastado,
                bonificacaoACadaCincoNovos, metaNacional, bonificacaoMetaNacional, exigeFoto, tipoPessoa,
                null, status, versao);
    }

}
