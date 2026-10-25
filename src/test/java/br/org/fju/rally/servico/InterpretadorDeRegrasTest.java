package br.org.fju.rally.servico;

import br.org.fju.rally.api.RespostaInterpretacaoMissoes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InterpretadorDeRegrasTest {
    @Test
    void corrigeUmaRespostaQueUsaCampoPessoaComoMetricaQuantitativa() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode respostaInvalida = resposta("""
                {"rally":null,"missoes":[{"nome":"Ação","descricao":"Registrar a ação.",
                "data_inicio":"01/11/2026","data_fim":"30/11/2026","regras_pontuacao":[
                {"tipo":"POR_QUANTIDADE","campo":"acao_realizada","pontos_por_unidade":10}],
                "campos_relatorio":[{"id":"acao_realizada","titulo":"Ação realizada?",
                "tipo":"selecao","obrigatorio":true,"opcoes":["sim","não"],"escopo":"PESSOA"}]}],
                "ambiguidades":[]}
                """);
        JsonNode respostaCorrigida = resposta("""
                {"rally":null,"missoes":[{"nome":"Ação","descricao":"Registrar a ação.",
                "data_inicio":"01/11/2026","data_fim":"30/11/2026","regras_pontuacao":[
                {"tipo":"POR_PESSOA","categoria":"INTEGRANTE","pontos":10,
                "quando":{"campo":"acao_realizada","valor":"sim","origem":"PESSOA"}}],
                "campos_relatorio":[{"id":"acao_realizada","titulo":"Ação realizada?",
                "tipo":"selecao","obrigatorio":true,"opcoes":["sim","não"],"escopo":"PESSOA"}]}],
                "ambiguidades":[]}
                """);
        RestClient.Builder builder = mock(RestClient.Builder.class);
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
        when(builder.clone()).thenReturn(builder);
        when(builder.requestFactory(any())).thenReturn(builder);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any(MediaType.class))).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        doReturn(bodySpec).when(bodySpec).body(any(Object.class));
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(eq(JsonNode.class))).thenReturn(respostaInvalida, respostaCorrigida);

        InterpretadorDeRegras interpretador = new InterpretadorDeRegras(builder, objectMapper,
                new ValidacaoConfiguracaoMissaoService(new RegrasPontuacaoService()),
                "https://openrouter.example", "chave-teste", "modelo-teste", "");

        RespostaInterpretacaoMissoes resposta = interpretador.interpretarMissoes("Pontue a ação por integrante.");

        assertEquals("POR_PESSOA", resposta.missoes().getFirst().regrasPontuacao().getFirst().get("tipo"));
        assertEquals("PESSOA", resposta.missoes().getFirst().camposRelatorio().getFirst().get("escopo"));
        verify(responseSpec, times(2)).body(eq(JsonNode.class));
        ArgumentCaptor<Object> corpos = ArgumentCaptor.forClass(Object.class);
        verify(bodySpec, times(2)).body(corpos.capture());
        Map<?, ?> corpoReparo = (Map<?, ?>) corpos.getAllValues().get(1);
        List<?> mensagens = (List<?>) corpoReparo.get("messages");
        String feedback = (String) ((Map<?, ?>) mensagens.get(2)).get("content");
        assertTrue(feedback.contains("As regras quantitativas precisam usar contagens conhecidas"));
        Map<?, ?> pedidoReparo = (Map<?, ?>) mensagens.get(4);
        assertTrue(((String) pedidoReparo.get("content")).contains("Corrija o JSON rejeitado"));
    }

    private JsonNode resposta(String conteudo) throws Exception {
        return new ObjectMapper().readTree("""
                {"choices":[{"finish_reason":"stop","message":{"content":%s}}]}
                """.formatted(new ObjectMapper().writeValueAsString(conteudo)));
    }
}
