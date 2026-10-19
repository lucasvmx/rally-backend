package br.org.fju.rally.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequisicaoLogin(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 128) String senha
) {}
