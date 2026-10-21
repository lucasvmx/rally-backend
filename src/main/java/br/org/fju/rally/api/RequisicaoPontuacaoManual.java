package br.org.fju.rally.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RequisicaoPontuacaoManual(
        @NotNull UUID nacaoId,
        @NotNull UUID triboChamadaId,
        @NotBlank @Size(max = 180) String atividade,
        @Min(1) @Max(1000000) Integer pontosPorTribo
) {}
