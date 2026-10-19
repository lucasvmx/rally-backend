package br.org.fju.rally.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RequisicaoRelatorio(
        @NotNull UUID missaoId,
        @NotNull UUID triboId,
        @Min(0) @Max(200) int quantidadePessoas,
        @Min(0) @Max(200) int quantidadeNovosAfastados,
        String observacoes,
        @Size(max = 400) List<@NotNull @Valid PessoaRelatorio> pessoas,
        @Size(max = 50) Map<String, Object> dados
) {
    public RequisicaoRelatorio(UUID missaoId, UUID triboId, int quantidadePessoas,
                               int quantidadeNovosAfastados, String observacoes) {
        this(missaoId, triboId, quantidadePessoas, quantidadeNovosAfastados, observacoes, List.of(), Map.of());
    }
}
