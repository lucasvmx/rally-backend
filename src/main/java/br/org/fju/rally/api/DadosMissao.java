package br.org.fju.rally.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DadosMissao(
        @NotBlank @Size(max = 180) String nome,
        @Size(max = 10000) String descricao,
        @NotNull LocalDate dataInicio,
        @NotNull LocalDate dataFim,
        Instant prazoEnvio,
        @Min(-1000000) @Max(1000000) Integer pontosPorPessoa,
        @Min(-1000000) @Max(1000000) Integer pontosPorNovoAfastado,
        @Min(-1000000) @Max(1000000) Integer bonificacaoACadaCincoNovos,
        @Min(-1000000) @Max(1000000) Integer metaNacional,
        @Min(-1000000) @Max(1000000) Integer bonificacaoMetaNacional,
        boolean exigeFoto,
        @Pattern(regexp = "integrante|novo_ou_afastado") String tipoPessoa,
        @Size(max = 2048) String linkGoogleForms,
        @Size(max = 100) List<Map<String, Object>> regrasPontuacao,
        @Size(max = 30) List<Map<String, Object>> camposRelatorio
) {
    public DadosMissao(String nome, String descricao, LocalDate dataInicio, LocalDate dataFim,
                       Instant prazoEnvio, Integer pontosPorPessoa, Integer pontosPorNovoAfastado,
                       Integer bonificacaoACadaCincoNovos, Integer metaNacional, Integer bonificacaoMetaNacional,
                       boolean exigeFoto, String tipoPessoa, String linkGoogleForms) {
        this(nome, descricao, dataInicio, dataFim, prazoEnvio, pontosPorPessoa, pontosPorNovoAfastado,
                bonificacaoACadaCincoNovos, metaNacional, bonificacaoMetaNacional, exigeFoto,
                tipoPessoa, linkGoogleForms, List.of(), List.of());
    }
}
