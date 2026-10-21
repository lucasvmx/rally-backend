package br.org.fju.rally.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record RequisicaoCriarMissoes(
        UUID rallyId,
        @Valid DadosRally rally,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid DadosMissao> missoes
) {
    public RequisicaoCriarMissoes(DadosRally rally, List<DadosMissao> missoes) {
        this(null, rally, missoes);
    }

    public RequisicaoCriarMissoes(List<DadosMissao> missoes) {
        this(null, null, missoes);
    }
}
