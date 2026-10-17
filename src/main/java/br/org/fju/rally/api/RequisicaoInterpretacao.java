package br.org.fju.rally.api;

import jakarta.validation.constraints.NotBlank;

public record RequisicaoInterpretacao(@NotBlank String texto) {}
