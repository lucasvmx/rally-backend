package br.org.fju.rally.api;

import tools.jackson.databind.JsonNode;

/** Respostas do formulário acompanhadas dos metadados da foto recebida. */
public record RespostaFormulario(
        JsonNode respostas,
        JsonNode definicao,
        boolean temFoto,
        boolean temFotoAnotada,
        Long contagemRostos
) {}
