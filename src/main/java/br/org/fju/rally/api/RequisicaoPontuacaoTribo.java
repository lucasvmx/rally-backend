package br.org.fju.rally.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RequisicaoPontuacaoTribo(
        @NotNull UUID nacaoId,
        @NotNull UUID triboId,
        @NotNull @Min(1) @Max(1000000) Integer pontos,
        @NotBlank @Size(max = 180) String justificativa,
        UUID missaoId,
        UUID rallyId
) {
    public RequisicaoPontuacaoTribo(UUID nacaoId, UUID triboId, Integer pontos,
                                    String justificativa, UUID missaoId) {
        this(nacaoId, triboId, pontos, justificativa, missaoId, null);
    }
}
