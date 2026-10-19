package br.org.fju.rally.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DadosRally(
        @NotBlank @Size(max = 180) String nome,
        @NotNull LocalDate dataInicio,
        @NotNull LocalDate dataFim
) {}
