package br.org.fju.rally.api;

import br.org.fju.rally.dominio.PontuacaoManual;

import java.time.Instant;
import java.util.UUID;

public record RespostaPontuacaoManual(
        UUID id,
        UUID rallyId,
        String rallyNome,
        UUID nacaoId,
        String nacaoNome,
        UUID triboChamadaId,
        String triboChamadaNome,
        String atividade,
        int pontosPorTribo,
        int totalTribosBeneficiadas,
        String registradoPor,
        Instant registradaEm
) {
    public static RespostaPontuacaoManual de(PontuacaoManual pontuacao) {
        return new RespostaPontuacaoManual(
                pontuacao.getId(),
                pontuacao.getRally().getId(),
                pontuacao.getRally().getNome(),
                pontuacao.getNacao().getId(),
                pontuacao.getNacao().getNome(),
                pontuacao.getTriboChamada().getId(),
                pontuacao.getTriboChamada().getNome(),
                pontuacao.getAtividade(),
                pontuacao.getPontosPorTribo(),
                pontuacao.getTribosDestinatarias().size(),
                pontuacao.getRegistradoPor(),
                pontuacao.getRegistradaEm());
    }
}
