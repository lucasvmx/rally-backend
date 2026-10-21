package br.org.fju.rally.api;

import br.org.fju.rally.dominio.Missao;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Dados mínimos da missão necessários para o formulário de envio público. */
public record RespostaMissaoPublica(UUID id, String nome, boolean exigeFoto,
                                    List<Map<String, Object>> camposRelatorio) {
    public static RespostaMissaoPublica de(Missao missao) {
        return new RespostaMissaoPublica(missao.getId(), missao.getNome(), missao.isExigeFoto(),
                camposPublicos(missao.getCamposRelatorio()));
    }

    private static List<Map<String, Object>> camposPublicos(List<Map<String, Object>> campos) {
        if (campos == null) return List.of();
        return campos.stream().map(campo -> {
            Map<String, Object> publico = new java.util.LinkedHashMap<>();
            publico.put("id", campo.get("id"));
            publico.put("titulo", campo.get("titulo"));
            publico.put("tipo", campo.get("tipo"));
            publico.put("obrigatorio", Boolean.TRUE.equals(campo.get("obrigatorio")));
            Object opcoes = campo.get("opcoes");
            publico.put("opcoes", opcoes instanceof List<?> lista
                    ? lista.stream().map(Object::toString).toList() : List.of());
            publico.put("escopo", Objects.toString(campo.getOrDefault("escopo", "RELATORIO"), "RELATORIO")
                    .toUpperCase(java.util.Locale.ROOT));
            return Map.copyOf(publico);
        }).toList();
    }
}
