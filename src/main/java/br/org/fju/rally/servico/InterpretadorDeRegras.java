package br.org.fju.rally.servico;

import br.org.fju.rally.api.RespostaInterpretacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InterpretadorDeRegras {
    private static final Pattern PONTOS = Pattern.compile("([0-9][0-9.]*)\\s*talentos", Pattern.CASE_INSENSITIVE);

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final String endereco;
    private final String chave;
    private final String modeloPrincipal;
    private final String modelosReserva;

    public InterpretadorDeRegras(RestClient.Builder restClientBuilder,
                                 ObjectMapper objectMapper,
                                 @Value("${rally.openrouter.endereco}") String endereco,
                                 @Value("${rally.openrouter.chave}") String chave,
                                 @Value("${rally.openrouter.modelo-regras}") String modeloPrincipal,
                                 @Value("${rally.openrouter.modelos-reserva}") String modelosReserva) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.endereco = endereco;
        this.chave = chave;
        this.modeloPrincipal = modeloPrincipal;
        this.modelosReserva = modelosReserva;
    }

    public RespostaInterpretacao interpretar(String texto) {
        if (chave == null || chave.isBlank() || modeloPrincipal == null || modeloPrincipal.isBlank()) {
            return interpretacaoLocal(texto, "OpenRouter não configurado; foi usada uma sugestão local para revisão.");
        }

        List<String> modelos = new ArrayList<>();
        modelos.add(modeloPrincipal);
        if (modelosReserva != null && !modelosReserva.isBlank()) {
            for (String modelo : modelosReserva.split(",")) {
                if (!modelo.isBlank()) {
                    modelos.add(modelo.trim());
                }
            }
        }

        for (String modelo : modelos) {
            try {
                return chamarOpenRouter(texto, modelo);
            } catch (RuntimeException excecao) {
                // O modelo reserva será tentado. O conteúdo do recado nunca é registrado.
            }
        }
        return interpretacaoLocal(texto, "Não foi possível consultar a IA; a sugestão local precisa ser revisada.");
    }

    private RespostaInterpretacao chamarOpenRouter(String texto, String modelo) {
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", promptSistema()),
                        Map.of("role", "user", "content", texto)));

        RestClient cliente = restClientBuilder.baseUrl(endereco).build();
        JsonNode resposta = cliente.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + chave)
                .header("X-Title", "Rally das Tribos")
                .body(corpo)
                .retrieve()
                .body(JsonNode.class);

        if (resposta == null) {
            throw new IllegalStateException("Resposta vazia do OpenRouter.");
        }
        String conteudo = resposta.at("/choices/0/message/content").asText();
        Map<String, Object> json = objectMapper.readValue(conteudo, new TypeReference<>() {});
        List<Map<String, Object>> regras = validar(json);
        return new RespostaInterpretacao("OPENROUTER", "Regras sugeridas pela IA. Confirme antes de publicar.", texto,
                regras, List.of());
    }

    private List<Map<String, Object>> validar(Map<String, Object> json) {
        Object regrasBrutas = json.get("regras");
        if (!(regrasBrutas instanceof List<?> lista) || lista.isEmpty()) {
            throw new IllegalArgumentException("A resposta não contém a lista de regras esperada.");
        }
        List<Map<String, Object>> regras = new ArrayList<>();
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> mapa)) {
                throw new IllegalArgumentException("Uma regra retornada não é um objeto JSON.");
            }
            Map<String, Object> regra = new HashMap<>();
            mapa.forEach((chave, valor) -> regra.put(String.valueOf(chave), valor));
            if (!regra.containsKey("nome_missao") || !regra.containsKey("exige_foto")) {
                throw new IllegalArgumentException("A regra não possui os campos obrigatórios em português.");
            }
            regras.add(regra);
        }
        return regras;
    }

    private RespostaInterpretacao interpretacaoLocal(String texto, String mensagem) {
        Matcher marcador = PONTOS.matcher(texto);
        int pontos = marcador.find() ? Integer.parseInt(marcador.group(1).replace(".", "")) : 0;
        Map<String, Object> regra = new HashMap<>();
        regra.put("nome_missao", "Revisar missão do recado");
        regra.put("data_inicio", LocalDate.now().toString());
        regra.put("data_fim", LocalDate.now().toString());
        regra.put("pontos_por_pessoa", pontos);
        regra.put("tipo_de_pessoa", "integrante");
        regra.put("exige_foto", texto.toLowerCase().contains("foto"));
        regra.put("bonificacoes", List.of());
        return new RespostaInterpretacao("FALLBACK_LOCAL", mensagem, texto, List.of(regra),
                List.of("A sugestão local não substitui a revisão da assistente."));
    }

    private String promptSistema() {
        return "Você interpreta recados do Rally das Tribos. Responda somente JSON válido, sem markdown, com "
                + "a chave raiz 'regras'. Todas as chaves e valores de domínio devem estar em português brasileiro. "
                + "Cada regra deve ter nome_missao, data_inicio, data_fim, prazo_envio, pontos_por_pessoa, "
                + "pontos_por_novo_afastado, tipo_de_pessoa, exige_foto e bonificacoes. Preserve missões separadas, "
                + "missões relâmpago e metas por nação. Nunca aprove pontuação; apenas sugira regras para revisão humana.";
    }
}
