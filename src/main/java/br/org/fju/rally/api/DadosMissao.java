package br.org.fju.rally.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record DadosMissao(
        @NotBlank @Size(max = 180) String nome,
        @Size(max = 10000) String descricao,
        @NotNull LocalDate dataInicio,
        @NotNull LocalDate dataFim,
        LocalDateTime prazoEnvio,
        @NotNull @Min(0) @Max(1000000) Integer pontosPorPessoa,
        @NotNull @Min(0) @Max(1000000) Integer pontosPorNovoAfastado,
        @NotNull @Min(0) @Max(1000000) Integer bonificacaoACadaCincoNovos,
        @NotNull @Min(0) @Max(1000000) Integer metaNacional,
        @NotNull @Min(0) @Max(1000000) Integer bonificacaoMetaNacional,
        boolean exigeFoto,
        @NotBlank @Pattern(regexp = "integrante|novo_ou_afastado") String tipoPessoa,
        @Size(max = 2048) String linkGoogleForms
) {}
