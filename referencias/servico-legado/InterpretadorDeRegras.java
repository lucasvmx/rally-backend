package br.org.fju.rally.servico;

import br.org.fju.rally.api.RespostaInterpretacao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InterpretadorDeRegras {
    private final RestClient cliente;
    private final ObjectMapper objeto;
    private final String chave;
    private final String modelo;
    private final String modelosReserva;

    public InterpretadorDeRegras(
            ObjectMapper objeto,
            @Value("${rally.openrouter.endereco}") String endereco,
            @Value("${rally.openrouter.chave}") String chave,
            @Value("${rally.openrouter.modelo-regras}") String modelo,
            @Value("${rally.openrouter.modelos-reserva}") String modelosReserva) {
        this.objeto = objeto;
        this.cliente = RestClient.builder().baseUrl(endereco).build();
        this.chave = chave;
        this.modelo = modelo;
        this.modelosReserva = modelosReserva;
    }

    public RespostaInterpretacao interpretar(String texto) {
        if (chave != null && !chave.isBlank() && modelo != null && !modelo.isBlank()) {
            try {
                return chamarOpenRouter(texto);
            } catch (RuntimeException ignored) {
                // A regra continua disponível em modo demonstrativo quando a IA não responde.
            }
        }
        return interpretarLocalmente(texto);
    }

    private RespostaInterpretacao chamarOpenRouter(String texto) {
        List<String> modelos = new ArrayList<>();
        modelos.add(modelo);
        if (modelosReserva != null && !modelosReserva.isBlank()) {
            modelos.addAll(List.of(modelosReserva.split(",")));
        }
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("models", modelos);
        corpo.put("temperature", 0.1);
        corpo.put("messages", List.of(
                Map.of("role", "system", "content", "Você interpreta regras de um rally de tribos. Responda somente em JSON válido, com chaves em português brasileiro. Não invente valores. Liste ambiguidades em um campo ambiguidades."),
                Map.of("role", "user", "content", texto)
        ));
        corpo.put("response_format", Map.of("type", "json_object"));

        JsonNode resposta = cliente.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + chave)
                .body(corpo)
                .retrieve()
                .body(JsonNode.class);
        String conteudo = resposta.path("choices").path(0).path("message").path("content").asText();
        JsonNode estruturado;
        try {
            estruturado = objeto.readTree(conteudo);
        } catch (Exception erro) {
            throw new IllegalArgumentException("A resposta da IA não é um JSON válido.", erro);
        }
        List<Map<String, Object>> regras = new ArrayList<>();
        if (estruturado.path("regras").isArray()) {
            estruturado.path("regras").forEach(regra -> regras.add(objeto.convertValue(regra, Map.class)));
        } else {
            regras.add(objeto.convertValue(estruturado, Map.class));
        }
        List<String> ambiguidades = new ArrayList<>();
        estruturado.path("ambiguidades").forEach(item -> ambiguidades.add(item.asText()));
        return new RespostaInterpretacao("OPENROUTER", "Regra interpretada pela IA. Revise antes de publicar.", texto, regras, ambiguidades);
    }

    private RespostaInterpretacao interpretarLocalmente(String texto) {
        Map<String, Object> regra = new LinkedHashMap<>();
        regra.put("nome_missao", "Missão extraída do recado");
        regra.put("texto_original", texto);
        regra.put("exige_foto", texto.toLowerCase().contains("foto"));
        regra.put("tipo_de_pessoa", texto.toLowerCase().contains("novo") || texto.toLowerCase().contains("afastado")
                ? "novo_ou_afastado" : "integrante");
        Matcher pontos = Pattern.compile("([0-9][0-9.]*)\\s+talentos\\s+por", Pattern.CASE_INSENSITIVE).matcher(texto);
        if (pontos.find()) {
            regra.put("pontos_por_pessoa", Integer.parseInt(pontos.group(1).replace(".", "")));
        } else {
            regra.put("pontos_por_pessoa", 0);
        }
        regra.put("ambiguidades", List.of("Modo demonstrativo: confirme nome, datas, prazo e bônus antes de publicar."));
        return new RespostaInterpretacao("MODO_DEMONSTRACAO", "A IA não está configurada; foi gerada uma sugestão local para demonstração.", texto, List.of(regra), List.of("Configure OPENROUTER_API_KEY para ativar a interpretação pelo OpenRouter."));
    }
}
