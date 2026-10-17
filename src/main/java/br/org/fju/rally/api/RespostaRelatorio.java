package br.org.fju.rally.api;

import br.org.fju.rally.dominio.Relatorio;

import java.time.LocalDateTime;
import java.util.UUID;

public record RespostaRelatorio(
        UUID id,
        UUID missaoId,
        String missaoNome,
        UUID triboId,
        String triboNome,
        String nacaoNome,
        int quantidadePessoas,
        int quantidadeNovosAfastados,
        String observacoes,
        LocalDateTime enviadoEm,
        boolean atrasado,
        String status,
        int pontosAprovados,
        String justificativaRevisao
) {
    public static RespostaRelatorio de(Relatorio relatorio) {
        return new RespostaRelatorio(
                relatorio.getId(), relatorio.getMissao().getId(), relatorio.getMissao().getNome(),
                relatorio.getTribo().getId(), relatorio.getTribo().getNome(), relatorio.getTribo().getNacao().getNome(),
                relatorio.getQuantidadePessoas(), relatorio.getQuantidadeNovosAfastados(), relatorio.getObservacoes(),
                relatorio.getEnviadoEm(), relatorio.isAtrasado(), relatorio.getStatus(),
                relatorio.getPontosAprovados(), relatorio.getJustificativaRevisao());
    }
}
