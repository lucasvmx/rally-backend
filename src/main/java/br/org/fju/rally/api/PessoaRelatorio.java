package br.org.fju.rally.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record PessoaRelatorio(
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Size(max = 80) String categoria,
        Map<String, Object> dados
) {}
