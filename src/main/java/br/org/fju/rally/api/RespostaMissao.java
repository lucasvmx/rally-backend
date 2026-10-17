package br.org.fju.rally.api;

import br.org.fju.rally.dominio.Missao;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
        String status,
        int versao
) {
    public static RespostaMissao de(Missao missao) {
        return new RespostaMissao(
                missao.getId(), missao.getNome(), missao.getDescricao(), missao.getDataInicio(), missao.getDataFim(),
                missao.getPrazoEnvio(), missao.getPontosPorPessoa(), missao.getPontosPorNovoAfastado(),
                missao.getBonificacaoACadaCincoNovos(), missao.getMetaNacional(), missao.getBonificacaoMetaNacional(),
                missao.isExigeFoto(), missao.getTipoPessoa(), missao.getStatus(), missao.getVersao());
    }
}
