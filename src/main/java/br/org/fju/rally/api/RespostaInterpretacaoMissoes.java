package br.org.fju.rally.api;

import java.util.List;

public record RespostaInterpretacaoMissoes(
        String origem,
        String mensagem,
        String textoOriginal,
        List<DadosMissao> missoes,
        List<String> linksGoogleForms,
        List<String> ambiguidades,
        DadosRally rally
) {
    public RespostaInterpretacaoMissoes(String origem, String mensagem, String textoOriginal,
                                        List<DadosMissao> missoes, List<String> linksGoogleForms,
                                        List<String> ambiguidades) {
        this(origem, mensagem, textoOriginal, missoes, linksGoogleForms, ambiguidades, null);
    }
}
