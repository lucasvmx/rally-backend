package br.org.fju.rally.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record MissaoSugerida(
        String nome,
        String descricao,
        LocalDate dataInicio,
        LocalDate dataFim,
        LocalDateTime prazoEnvio,
        boolean exigeFoto,
        String linkGoogleForms,
        List<Map<String, Object>> regrasPontuacao,
        List<Map<String, Object>> camposRelatorio
) {}
