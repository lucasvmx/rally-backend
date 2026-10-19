package br.org.fju.rally.api;

import br.org.fju.rally.dominio.Rally;

import java.time.LocalDate;
import java.util.UUID;

public record RespostaRally(UUID id, String nome, LocalDate dataInicio, LocalDate dataFim, boolean ativo) {
    public static RespostaRally de(Rally rally, LocalDate hoje) {
        return new RespostaRally(rally.getId(), rally.getNome(), rally.getDataInicio(), rally.getDataFim(),
                rally.contem(hoje));
    }
}
