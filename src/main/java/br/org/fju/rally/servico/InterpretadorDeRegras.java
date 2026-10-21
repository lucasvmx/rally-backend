package br.org.fju.rally.servico;

import br.org.fju.rally.api.DadosRally;
import br.org.fju.rally.api.MissaoSugerida;
import br.org.fju.rally.api.RespostaInterpretacao;
import br.org.fju.rally.api.RespostaInterpretacaoMissoes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class InterpretadorDeRegras {
    private static final Pattern PONTOS = Pattern.compile("([0-9][0-9.]*)\\s*talentos", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_FORMULARIO = Pattern.compile(
            "https?://(?:forms\\.gle/[^\\s<>]+|docs\\.google\\.com/forms/[^\\s<>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CABECALHO_MISSAO = Pattern.compile("(?m)^\\s*(?:➡️|➡|➜|→)\\s*");

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

    public RespostaInterpretacaoMissoes interpretarMissoes(String texto) {
        if (chave == null || chave.isBlank() || modeloPrincipal == null || modeloPrincipal.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A interpretação de missões exige OPENROUTER_API_KEY e OPENROUTER_MODELO_REGRAS configurados.");
        }

        List<String> modelos = new ArrayList<>();
        modelos.add(modeloPrincipal);
        if (modelosReserva != null && !modelosReserva.isBlank()) {
            for (String modelo : modelosReserva.split(",")) {
                if (!modelo.isBlank()) modelos.add(modelo.trim());
            }
        }

        RuntimeException ultimaFalha = null;
        for (String modelo : modelos) {
            try {
                return chamarOpenRouterParaMissoes(texto, modelo);
            } catch (RuntimeException excecao) {
                ultimaFalha = excecao;
            }
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível interpretar as missões com a IA. Tente novamente mais tarde.", ultimaFalha);
    }

    private RespostaInterpretacaoMissoes chamarOpenRouterParaMissoes(String texto, String modelo) {
        Map<String, Object> corpo = Map.of(
                "model", modelo,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", promptSistemaMissoes()),
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
        if (resposta == null) throw new IllegalStateException("Resposta vazia do OpenRouter.");

        String conteudo = resposta.at("/choices/0/message/content").asText();
        JsonNode json = objectMapper.readTree(conteudo);
        JsonNode regras = json.path("missoes");
        if (!regras.isArray()) regras = json.path("regras");
        if (!regras.isArray() || regras.size() == 0 || regras.size() > 100) {
            throw new IllegalArgumentException("A resposta da IA não contém uma lista válida de missões.");
        }

        List<String> linksEncontrados = extrairLinksGoogleForms(texto);
        List<List<String>> linksPorSecao = extrairLinksPorSecao(texto);
        List<String> ambiguidades = new ArrayList<>();
        JsonNode ambiguidadesJson = json.path("ambiguidades");
        if (ambiguidadesJson.isArray()) ambiguidadesJson.forEach(item -> {
            if (!item.asText().isBlank()) ambiguidades.add(item.asText());
        });
        List<MissaoSugerida> missoes = new ArrayList<>();
        for (int indice = 0; indice < regras.size(); indice++) {
            JsonNode regra = regras.get(indice);
            if (!regra.isObject()) throw new IllegalArgumentException("Uma missão da IA não é um objeto JSON.");
            String link = texto(regra, "link_google_forms", "link_formulario_google", "google_forms_url");
            if (link != null && !linksEncontrados.contains(limparLink(link))) link = null;
            if (link == null && linksEncontrados.size() == 1) link = linksEncontrados.getFirst();
            if (link == null && regras.size() == linksPorSecao.size() && indice < linksPorSecao.size()
                    && !linksPorSecao.get(indice).isEmpty()) {
                link = linksPorSecao.get(indice).getFirst();
            }
            missoes.add(missaoInterpretada(regra, link, ambiguidades));
        }

        DadosRally rally = rallyInterpretado(json.path("rally"), ambiguidades);

        if (linksEncontrados.size() > 1 && missoes.stream().noneMatch(missao -> missao.linkGoogleForms() != null)) {
            ambiguidades.add("Escolha manualmente o link de formulário correspondente a cada missão.");
        }

        return new RespostaInterpretacaoMissoes("OPENROUTER",
                "Missões sugeridas pela IA. Revise as regras antes de publicar; condições fora dos campos de pontuação ficam na descrição e não são calculadas automaticamente.", texto,
                missoes, linksEncontrados, ambiguidades, rally);
    }

    private MissaoSugerida missaoInterpretada(JsonNode regra, String link, List<String> ambiguidades) {
        String nome = texto(regra, "nome", "nome_missao", "titulo");
        String descricao = texto(regra, "descricao", "regras_resumo", "resumo");
        LocalDate inicio = data(regra, "data_inicio", "inicio");
        LocalDate fim = data(regra, "data_fim", "fim");
        LocalDateTime prazo = dataHora(regra, fim);
        if (nome == null) ambiguidades.add("Informe o nome de uma das missões antes de publicar.");
        if (inicio == null || fim == null) ambiguidades.add("Complete as datas de início e fim da missão "
                + (nome == null ? "sem nome" : "“" + nome + "”") + ".");
        if (prazo == null) {
            ambiguidades.add("Confirme se a missão " + (nome == null ? "sem nome" : "“" + nome + "”")
                    + " tem prazo de envio.");
        }
        List<Map<String, Object>> regrasPontuacao = objetos(regra.path("regras_pontuacao"));
        if (regrasPontuacao.isEmpty()) {
            ambiguidades.add("Revise se todas as regras de pontuação da missão "
                    + (nome == null ? "sem nome" : "“" + nome + "”") + " foram estruturadas.");
        }
        if (!temCampo(regra, "exige_foto", "foto_obrigatoria")) {
            ambiguidades.add("Confirme se a missão " + (nome == null ? "sem nome" : "“" + nome + "”")
                    + " exige foto.");
        }
        boolean exigeFoto = booleano(regra, "exige_foto", "foto_obrigatoria");
        List<Map<String, Object>> camposRelatorio = objetos(regra.path("campos_relatorio"));
        return new MissaoSugerida(nome, descricao, inicio, fim, prazo, exigeFoto, link,
                regrasPontuacao, camposRelatorio);
    }

    private DadosRally rallyInterpretado(JsonNode json, List<String> ambiguidades) {
        if (json == null || !json.isObject()) return null;
        String nome = texto(json, "nome", "nome_rally", "titulo");
        LocalDate inicio = data(json, "data_inicio", "inicio");
        LocalDate fim = data(json, "data_fim", "fim");
        if (nome == null || inicio == null || fim == null) {
            ambiguidades.add("Confirme o nome e as datas de início e fim do rally.");
            return null;
        }
        return new DadosRally(nome, inicio, fim);
    }

    private List<Map<String, Object>> objetos(JsonNode valor) {
        if (!valor.isArray()) return List.of();
        List<Map<String, Object>> objetos = new ArrayList<>();
        for (JsonNode item : valor) {
            if (item.isObject()) objetos.add(objectMapper.convertValue(item,
                    new TypeReference<Map<String, Object>>() {}));
        }
        return List.copyOf(objetos);
    }

    private List<String> extrairLinksGoogleForms(String texto) {
        Set<String> links = new LinkedHashSet<>();
        Matcher matcher = LINK_FORMULARIO.matcher(texto);
        while (matcher.find()) {
            String link = limparLink(matcher.group());
            if (linkGoogleFormsValido(link)) links.add(link);
        }
        return List.copyOf(links);
    }

    private List<List<String>> extrairLinksPorSecao(String texto) {
        Matcher matcher = CABECALHO_MISSAO.matcher(texto);
        List<Integer> inicios = new ArrayList<>();
        while (matcher.find()) inicios.add(matcher.end());
        List<List<String>> secoes = new ArrayList<>();
        for (int indice = 0; indice < inicios.size(); indice++) {
            int inicio = inicios.get(indice);
            int fim = indice + 1 < inicios.size() ? inicios.get(indice + 1) : texto.length();
            secoes.add(extrairLinksGoogleForms(texto.substring(inicio, fim)));
        }
        return secoes;
    }

    private static String limparLink(String link) {
        String limpo = link.trim();
        while (!limpo.isEmpty() && ".,;:!?*)]}".indexOf(limpo.charAt(limpo.length() - 1)) >= 0) {
            limpo = limpo.substring(0, limpo.length() - 1);
        }
        return limpo;
    }

    public static boolean linkGoogleFormsValido(String link) {
        if (link == null || link.isBlank() || link.length() > 2048) return false;
        try {
            URI uri = URI.create(link);
            String host = uri.getHost();
            String caminho = uri.getPath();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null && uri.getPort() == -1
                    && caminho != null
                    && (("forms.gle".equalsIgnoreCase(host) && caminho.length() > 1)
                    || ("docs.google.com".equalsIgnoreCase(host) && caminho.startsWith("/forms/")));
        } catch (RuntimeException excecao) {
            return false;
        }
    }

    private static String texto(JsonNode objeto, String... nomes) {
        for (String nome : nomes) {
            JsonNode valor = objeto.get(nome);
            if (valor != null && !valor.isNull() && !valor.asText().isBlank()) return valor.asText().trim();
        }
        return null;
    }

    private static boolean temCampo(JsonNode objeto, String... nomes) {
        for (String nome : nomes) {
            JsonNode valor = objeto.get(nome);
            if (valor != null && !valor.isNull()) return true;
        }
        return false;
    }

    private static LocalDate data(JsonNode objeto, String... nomes) {
        String valor = texto(objeto, nomes);
        if (valor == null) return null;
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException ignorada) {
            try {
                return LocalDate.parse(valor, DateTimeFormatter.ofPattern("d/M/uuuu"));
            } catch (DateTimeParseException tambemIgnorada) {
                return null;
            }
        }
    }

    private static LocalDateTime dataHora(JsonNode objeto, LocalDate dataFim) {
        String valor = texto(objeto, "prazo_envio", "prazo");
        if (valor == null) return null;
        try {
            return LocalDateTime.parse(valor, DateTimeFormatter.ofPattern("d/M/uuuu HH:mm:ss"));
        } catch (DateTimeParseException ignorada) {
            try {
                return LocalDateTime.parse(valor);
            } catch (DateTimeParseException tambemIgnorada) {
                try {
                    return LocalDate.parse(valor, DateTimeFormatter.ofPattern("d/M/uuuu")).atStartOfDay();
                } catch (DateTimeParseException dataBrasileiraInvalida) {
                    try {
                        return LocalDate.parse(valor).atStartOfDay();
                    } catch (DateTimeParseException dataIsoInvalida) {
                        try {
                            return dataFim == null ? null : dataFim.atTime(LocalTime.parse(valor));
                        } catch (DateTimeParseException naoEhHorario) {
                            return null;
                        }
                    }
                }
            }
        }
    }

    private static boolean booleano(JsonNode objeto, String... nomes) {
        for (String nome : nomes) {
            JsonNode valor = objeto.get(nome);
            if (valor == null || valor.isNull()) continue;
            if (valor.isBoolean()) return valor.asBoolean();
            String texto = valor.asText().toLowerCase();
            return texto.equals("true") || texto.equals("sim") || texto.equals("yes");
        }
        return false;
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
                + "pontos_por_novo_afastado, tipo_de_pessoa, exige_foto e bonificacoes. Use o formato brasileiro "
                + "DD/MM/AAAA para datas e DD/MM/AAAA HH:mm:ss para prazos com data e hora; use null quando não houver prazo. "
                + "Não use o padrão ISO 8601 nem separe data e hora com 'T'. Preserve missões separadas, "
                + "missões relâmpago e metas por nação. O escopo padrão de cada missão é TRIBO: credite os pontos de cada relatório à tribo correspondente. "
                + "A pontuação da nação é somente a soma dos pontos de suas tribos, sem pontuação separada para a nação. "
                + "Metas nacionais podem agregar os relatórios das tribos, mas distribua a premiação às tribos. "
                + "Bonificações iguais para todas as tribos, acionadas pela conclusão de uma atividade por uma tribo chamada, devem ser registradas manualmente. "
                + "Nunca aprove pontuação; apenas sugira regras para revisão humana.";
    }

    private String promptSistemaMissoes() {
        return "Você transforma mensagens de Rally em missões estruturadas. Responda somente JSON válido, sem markdown, "
                + "com a chave raiz 'rally' contendo nome, data_inicio e data_fim do Rally, 'missoes' contendo uma missão por atividade descrita, e uma chave 'ambiguidades' com "
                + "dúvidas que exijam revisão humana. Para cada missão use exatamente os campos: nome, descricao, "
                + "data_inicio (DD/MM/AAAA), data_fim (DD/MM/AAAA), prazo_envio (DD/MM/AAAA HH:mm:ss ou null), "
                + "exige_foto, link_google_forms (URL HTTPS literal da mensagem ou null), regras_pontuacao e "
                + "campos_relatorio. Toda pontuação, inclusive pontos por pessoa, por quantidade e quaisquer "
                + "bonificações, deve aparecer somente em regras_pontuacao; não retorne campos fixos de pontuação "
                + "na missão. Use POR_PESSOA com categoria INTEGRANTE ou NOVO_AFASTADO para pontuar pessoas. "
                + "Use A_CADA para bonificações recorrentes a cada quantidade configurável, META para metas e "
                + "META_COMPOSTA para recompensas com critérios alternativos. "
                + "Use o formato brasileiro DD/MM/AAAA em todas as datas do Rally e das missões e DD/MM/AAAA HH:mm:ss "
                + "nos prazos com data e hora. Não use o padrão ISO 8601 nem separe data e hora com 'T'. "
                + "O escopo padrão de toda missão é TRIBO: avalie sua pontuação separadamente para cada tribo e credite os pontos à tribo do relatório. "
                + "Quando uma regra aceitar escopo, use TRIBO por padrão; use NACAO somente se a atividade exigir explicitamente um cálculo agregado nacional, "
                + "e distribua pontuação nacional com distribuir_para TRIBOS quando todas as tribos da nação devem receber o bônus ou TRIBO_GERADORA quando somente as tribos "
                + "com relatórios aprovados que contribuem para a regra devem recebê-lo. A pontuação de uma nação é sempre a soma dos pontos finais de suas tribos; "
                + "não crie pontuação separada para a nação nem conte novamente um bônus nacional no total dela. Se o texto não esclarecer quais tribos recebem o bônus, "
                + "registre essa decisão em ambiguidades em vez de presumir. "
                + "Estruture regras_pontuacao como uma lista de objetos JSON: POR_PESSOA (categoria, pontos, quando), "
                + "POR_QUANTIDADE (campo, pontos_por_unidade, quando), POR_RELATORIO (pontos, quando), A_CADA "
                + "(campo, quantidade, pontos, escopo TRIBO ou NACAO), META (campo, quantidade, pontos, escopo "
                + "TRIBO ou NACAO, distribuir_para TRIBOS), META_COMPOSTA (escopo, pontos, condicao_agregada), "
                + "FAIXAS (campo, escopo, estrategia MAIOR_FAIXA ou "
                + "CUMULATIVA, faixas com minimo e pontos), PERCENTUAL (campo, campo_base, percentual, pontos, "
                + "escopo, distribuir_para) ou FAIXAS_PERCENTUAIS (campo, campo_base, escopo, estrategia e faixas "
                + "com minimo_percentual e pontos). Para percentual, campo é o numerador e campo_base é o denominador; "
                + "ambos devem ser números informados no relatório ou contagens de pessoas suportadas. Escreva uma condição "
                + "como campo, valor, operador e origem (RELATORIO ou PESSOA); use 'todas' ou 'alguma' para combinar "
                + "condições. Em META_COMPOSTA, condicao_agregada aceita 'todas' ou 'alguma' com folhas campo, "
                + "operador e valor; cada campo é somado separadamente no escopo antes de avaliar o grupo. "
                + "Cada grupo pode ter até 30 itens; limite a árvore agregada à profundidade 8 (raiz no nível 0) "
                + "e a 200 nós. A condição "
                + "quando da regra continua disponível para filtrar os relatórios antes de agregar as métricas. Use "
                + "quando_pessoa opcional numa folha para contar apenas pessoas que satisfaçam a condição. Use "
                + "META_COMPOSTA para uma única recompensa que possa ser alcançada por critérios alternativos, "
                + "como 10 carros OU 20 convidados; não crie duas metas independentes com a mesma recompensa, pois "
                + "isso poderia pagar em dobro quando ambas forem atingidas. Em POR_PESSOA, quando avalia os dados da pessoa por padrão; informe origem RELATORIO para "
                + "uma condição do relatório. Em regras agregadas, quando filtra o relatório e quando_pessoa filtra as "
                + "pessoas antes da contagem. Use quando para preservar condições de modalidade, "
                + "camiseta, presença, foto, dia, local e demais critérios. Defina campos_relatorio como a lista de "
                + "campos id/titulo/tipo/obrigatorio/opcoes/escopo necessários às regras; escopo é RELATORIO ou PESSOA. "
                + "Campos PESSOA podem ser preenchidos individualmente para cada nome enviado. Preserve no campo descricao todas as "
                + "condições que não puderem ser estruturadas e registre-as em ambiguidades. Quando pontos forem concedidos por pessoa que cumpre uma ação, "
                + "crie um campo PESSOA para registrar essa ação e use POR_PESSOA com a condição correspondente; se a mesma ação contar para uma meta coletiva, "
                + "use META com campo pessoas e o mesmo filtro em quando_pessoa para evitar divergência entre a recompensa individual e o total da meta. "
                + "Quando a quantidade for informada diretamente por relatório, crie um campo RELATORIO numérico e reutilize o mesmo campo na pontuação por unidade e na meta. "
                + "Não crie campos duplicados para representar a mesma quantidade. Não invente datas, "
                + "valores nem links; quando faltar informação, use zero/null e registre a ambiguidade. Uma missão "
                + "com mais de uma data de realização pode ter um período entre data_inicio e data_fim. A data de "
                + "referência é " + LocalDate.now(ZoneId.of("America/Sao_Paulo")) + "; se o ano não estiver explícito, "
                + "use-o apenas como sugestão e registre que o ano precisa ser confirmado. Registre em ambiguidades "
                + "toda condição de bônus que não possa ser calculada com os campos estruturados. Nunca aprove "
                + "pontuação: produza somente sugestões para revisão humana.";
    }
}
