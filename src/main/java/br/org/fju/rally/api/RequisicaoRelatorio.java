package br.org.fju.rally.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RequisicaoRelatorio(
        @NotNull UUID missaoId,
        @NotNull UUID triboId,
        @Min(0) @Max(200) int quantidadePessoas,
        @Min(0) @Max(200) int quantidadeNovosAfastados,
        String observacoes
) {}
