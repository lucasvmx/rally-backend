package br.org.fju.rally.api;

import br.org.fju.rally.dominio.Missao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RespostaMissao(
        UUID id,
        String nome,
        String descricao,
        LocalDate dataInicio,
        LocalDate dataFim,
        LocalDateTime prazoEnvio,
        int pontosPorPessoa,
        int pontosPorNovoAfastado,
        int bonificacaoACadaCincoNovos,
        int metaNacional,
        int bonificacaoMetaNacional,
        boolean exigeFoto,
        String tipoPessoa,
        String linkGoogleForms,
        String status,
        int versao,
        UUID rallyId,
        String rallyNome,
        List<Map<String, Object>> regrasPontuacao,
        List<Map<String, Object>> camposRelatorio
) {
    public RespostaMissao(UUID id, String nome, String descricao, LocalDate dataInicio, LocalDate dataFim,
                          LocalDateTime prazoEnvio, int pontosPorPessoa, int pontosPorNovoAfastado,
                          int bonificacaoACadaCincoNovos, int metaNacional, int bonificacaoMetaNacional,
                          boolean exigeFoto, String tipoPessoa, String linkGoogleForms, String status, int versao) {
        this(id, nome, descricao, dataInicio, dataFim, prazoEnvio, pontosPorPessoa, pontosPorNovoAfastado,
                bonificacaoACadaCincoNovos, metaNacional, bonificacaoMetaNacional, exigeFoto, tipoPessoa,
                linkGoogleForms, status, versao, null, null, List.of(), List.of());
    }

    public static RespostaMissao de(Missao missao) {
        return new RespostaMissao(
                missao.getId(), missao.getNome(), missao.getDescricao(), missao.getDataInicio(), missao.getDataFim(),
                missao.getPrazoEnvio(), missao.getPontosPorPessoa(), missao.getPontosPorNovoAfastado(),
                missao.getBonificacaoACadaCincoNovos(), missao.getMetaNacional(), missao.getBonificacaoMetaNacional(),
                missao.isExigeFoto(), missao.getTipoPessoa(), missao.getLinkGoogleForms(), missao.getStatus(),
                missao.getVersao(), missao.getRally() == null ? null : missao.getRally().getId(),
                missao.getRally() == null ? null : missao.getRally().getNome(), missao.getRegrasPontuacao(),
                missao.getCamposRelatorio());
    }
}
