package br.org.fju.rally.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoDefinirSenha(
        @NotBlank @Size(min = 12, max = 128) String senha
) {}
