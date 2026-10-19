package br.org.fju.rally.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoInterpretacao(@NotBlank @Size(max = 40000) String texto) {}
