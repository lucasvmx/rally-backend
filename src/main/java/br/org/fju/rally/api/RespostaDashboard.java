package br.org.fju.rally.api;

import java.util.List;

public record RespostaDashboard(RespostaRally rally, Missoes missoes, Relatorios relatorios,
                                List<RespostaPlacar> ranking) {
    public record Missoes(long total) {}
    public record Relatorios(long total, long enviados, long aprovados, long rejeitados,
                             long pessoasAprovadas, long novosAfastadosAprovados) {}
}
