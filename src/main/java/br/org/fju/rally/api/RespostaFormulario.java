package br.org.fju.rally.api;

import tools.jackson.databind.JsonNode;

/** Respostas do formulário acompanhadas dos metadados da imagem analisada. */
public record RespostaFormulario(
        JsonNode respostas,
        JsonNode definicao,
        boolean temFoto,
        boolean temFotoAnotada,
        Long contagemRostos
) {}
