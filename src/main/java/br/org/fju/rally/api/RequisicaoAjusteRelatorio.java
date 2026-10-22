package br.org.fju.rally.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Participantes conferidos pela assistência antes do cálculo da pontuação. */
public record RequisicaoAjusteRelatorio(
        @NotNull @Size(max = 400) List<@NotNull @Valid PessoaRelatorio> pessoas
) {}
