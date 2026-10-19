package br.org.fju.rally.api;

import br.org.fju.rally.dominio.Tribo;

import java.util.UUID;

public record RespostaTribo(UUID id, String nome, boolean ativa, UUID nacaoId, String nacaoNome) {
    public static RespostaTribo de(Tribo tribo) {
        return new RespostaTribo(tribo.getId(), tribo.getNome(), tribo.isAtiva(),
            tribo.getNacao().getId(), tribo.getNacao().getNome());
    }
}
