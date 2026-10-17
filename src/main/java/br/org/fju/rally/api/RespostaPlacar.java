package br.org.fju.rally.api;

import java.util.UUID;

public record RespostaPlacar(
        int posicao,
        UUID triboId,
        String triboNome,
        String nacaoNome,
        long pontos,
        long pessoasAprovadas,
        long relatoriosAprovados,
        long bonusNacional
) {}
