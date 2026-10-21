package br.org.fju.rally.api;

import java.util.List;
import java.util.Map;

public record RespostaDadosMissaoRelatorio(
        Map<String, Object> dados,
        List<Pessoa> pessoas
) {
    public record Pessoa(
            String nome,
            String categoria,
            Map<String, Object> dados
    ) {}
}
