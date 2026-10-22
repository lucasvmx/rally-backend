package br.org.fju.rally.api;

import br.org.fju.rally.dominio.Relatorio;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
        Instant enviadoEm,
        boolean atrasado,
        String status,
        int pontosAprovados,
        String justificativaRevisao,
        List<Map<String, Object>> avisos
) {
    public RespostaRelatorio(UUID id, UUID missaoId, String missaoNome, UUID triboId, String triboNome,
                             String nacaoNome, int quantidadePessoas, int quantidadeNovosAfastados,
                             String observacoes, Instant enviadoEm, boolean atrasado, String status,
                             int pontosAprovados, String justificativaRevisao) {
        this(id, missaoId, missaoNome, triboId, triboNome, nacaoNome, quantidadePessoas,
                quantidadeNovosAfastados, observacoes, enviadoEm, atrasado, status,
                pontosAprovados, justificativaRevisao, List.of());
    }

    public static RespostaRelatorio de(Relatorio relatorio) {
        return new RespostaRelatorio(
                relatorio.getId(), relatorio.getMissao().getId(), relatorio.getMissao().getNome(),
                relatorio.getTribo().getId(), relatorio.getTribo().getNome(), relatorio.getTribo().getNacao().getNome(),
                relatorio.getQuantidadePessoas(), relatorio.getQuantidadeNovosAfastados(), relatorio.getObservacoes(),
                relatorio.getEnviadoEm(), relatorio.isAtrasado(), relatorio.getStatus(),
                relatorio.getPontosAprovados(), relatorio.getJustificativaRevisao(), relatorio.getAvisos());
    }
}
