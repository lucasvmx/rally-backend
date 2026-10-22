package br.org.fju.rally.api;

import java.util.UUID;

public record RespostaInterpretacaoAssincrona(
        UUID id,
        Estado estado,
        RespostaInterpretacaoMissoes resultado,
        String mensagemErro) {

    public enum Estado {
        NA_FILA,
        PROCESSANDO,
        CONCLUIDA,
        FALHOU,
        CANCELADA
    }
}
