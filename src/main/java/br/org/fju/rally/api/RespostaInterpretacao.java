package br.org.fju.rally.api;

import java.util.List;
import java.util.Map;

public record RespostaInterpretacao(
        String origem,
        String mensagem,
        String textoOriginal,
        List<Map<String, Object>> regras,
        List<String> ambiguidades
) {}
