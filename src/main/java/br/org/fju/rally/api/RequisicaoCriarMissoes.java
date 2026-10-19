package br.org.fju.rally.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RequisicaoCriarMissoes(
        @NotEmpty @Size(max = 100) List<@NotNull @Valid DadosMissao> missoes
) {}
