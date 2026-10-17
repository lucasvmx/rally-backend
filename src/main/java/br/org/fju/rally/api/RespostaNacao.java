package br.org.fju.rally.api;

import br.org.fju.rally.dominio.Nacao;

import java.util.UUID;

public record RespostaNacao(UUID id, String nome) {
    public static RespostaNacao de(Nacao nacao) {
        return new RespostaNacao(nacao.getId(), nacao.getNome());
    }
}
