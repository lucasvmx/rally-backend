package br.org.fju.rally.servico;

import br.org.fju.rally.api.DadosMissao;
import br.org.fju.rally.dominio.PessoaPontuada;
import br.org.fju.rally.dominio.Relatorio;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class RegrasPontuacaoService {
    private static final List<String> TIPOS = List.of("POR_PESSOA", "POR_QUANTIDADE", "POR_RELATORIO",
            "A_CADA", "META", "META_COMPOSTA", "FAIXAS", "PERCENTUAL", "FAIXAS_PERCENTUAIS");

    public record ReferenciaMetrica(String campo, Object quandoPessoa) {}

    public List<Map<String, Object>> normalizar(List<Map<String, Object>> regras, DadosMissao dados) {
        if (regras == null || regras.isEmpty()) return criarRegrasLegadas(dados);
        if (regras.size() > 100) throw erro("A missão pode ter no máximo 100 regras de pontuação.");
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map<String, Object> entrada : regras) {
            if (entrada == null) throw erro("Há uma regra de pontuação vazia.");
            Map<String, Object> regra = new LinkedHashMap<>(entrada);
            String tipo = texto(regra.get("tipo"));
            if (tipo == null) throw erro("Toda regra precisa informar o tipo.");
            tipo = tipo.toUpperCase(Locale.ROOT);
            if (tipo.equals("PONTOS_POR_PESSOA")) tipo = "POR_PESSOA";
            if (tipo.equals("PONTOS_POR_QUANTIDADE")) tipo = "POR_QUANTIDADE";
            if (tipo.equals("BONUS_A_CADA")) tipo = "A_CADA";
            if (tipo.equals("BONUS_META")) tipo = "META";
            if (tipo.equals("BONUS_META_COMPOSTA")) tipo = "META_COMPOSTA";
            if (tipo.equals("BONUS_FAIXAS")) tipo = "FAIXAS";
            if (!TIPOS.contains(tipo)) throw erro("Tipo de regra não suportado: " + tipo + ".");
            regra.put("tipo", tipo);
            validarRegra(regra, tipo);
            resultado.add(Collections.unmodifiableMap(new LinkedHashMap<>(regra)));
        }
        return List.copyOf(resultado);
    }

    public int calcularPontos(Relatorio relatorio, List<PessoaPontuada> pessoas) {
        List<Map<String, Object>> regras = relatorio.getMissao().getRegrasPontuacao();
        if (regras == null || regras.isEmpty()) return calcularPontuacaoLegada(relatorio, pessoas);
        long pontos = 0;
        for (Map<String, Object> regra : regras) {
            String tipo = texto(regra.get("tipo"));
            if ("POR_PESSOA".equals(tipo)) {
                String categoria = normalizarCategoria(texto(regra.get("categoria")));
                long porPessoa = inteiro(regra.get("pontos"), 0);
                for (PessoaPontuada pessoa : pessoas) {
                    if (correspondeCategoria(categoria, pessoa.getCategoria())
                            && condicaoAtendida(regra.get("quando"), relatorio.getDados(), pessoa.getDados(), "PESSOA")) {
                        pontos += porPessoa;
                    }
                }
            } else if ("POR_QUANTIDADE".equals(tipo)
                    && condicaoAtendida(regra.get("quando"), relatorio.getDados(), Map.of())) {
                pontos += metric(relatorio, pessoas, texto(regra.get("campo")), regra.get("quando_pessoa"))
                        .multiply(BigDecimal.valueOf(inteiro(regra.get("pontos_por_unidade"), 0))).longValue();
            } else if ("POR_RELATORIO".equals(tipo)
                    && condicaoAtendida(regra.get("quando"), relatorio.getDados(), Map.of())) {
                pontos += inteiro(regra.get("pontos"), 0);
            }
        }
        if (pontos > Integer.MAX_VALUE || pontos < 0) throw erro("A pontuação calculada está fora do limite permitido.");
        return (int) pontos;
    }

    public BigDecimal metric(Relatorio relatorio, List<PessoaPontuada> pessoas, String campo) {
        return metric(relatorio, pessoas, campo, null);
    }

    public BigDecimal metric(Relatorio relatorio, List<PessoaPontuada> pessoas, String campo,
                             Object quandoPessoa) {
        if (campo == null || campo.isBlank()) return BigDecimal.ZERO;
        String chave = campo.toLowerCase(Locale.ROOT);
        List<PessoaPontuada> consideradas = quandoPessoa == null ? pessoas : pessoas.stream()
                .filter(pessoa -> condicaoAtendida(quandoPessoa, Map.of(), pessoa.getDados(), "PESSOA"))
                .toList();
        if ("integrantes".equals(chave)) {
            return BigDecimal.valueOf(consideradas.stream()
                    .filter(pessoa -> !categoriaNovo(normalizarCategoria(pessoa.getCategoria()))).count());
        }
        if (List.of("pessoas", "total_pessoas").contains(chave)) {
            return BigDecimal.valueOf(consideradas.size());
        }
        if (List.of("novos_afastados", "novos", "afastados").contains(chave)) {
            return BigDecimal.valueOf(consideradas.stream()
                    .filter(pessoa -> categoriaNovo(normalizarCategoria(pessoa.getCategoria()))).count());
        }
        if ("convidados".equals(chave)) {
            return BigDecimal.valueOf(consideradas.stream()
                    .filter(pessoa -> normalizarCategoria(pessoa.getCategoria()).equals("CONVIDADO")).count());
        }
        if (quandoPessoa != null) throw erro("A condição por pessoa só pode ser usada com uma contagem de pessoas.");
        return numero(relatorio.getDados().get(campo));
    }

    public long calcularBonusAgregado(Map<String, Object> regra, BigDecimal quantidade) {
        return calcularBonusAgregado(regra, quantidade, Map.of());
    }

    public long calcularBonusAgregado(Map<String, Object> regra, BigDecimal quantidade,
                                     Map<ReferenciaMetrica, BigDecimal> metricas) {
        String tipo = texto(regra.get("tipo"));
        long pontos = inteiro(regra.get("pontos"), 0);
        if ("A_CADA".equals(tipo)) {
            long bloco = inteiro(regra.get("quantidade"), 0);
            return bloco <= 0 ? 0 : quantidade.divideToIntegralValue(BigDecimal.valueOf(bloco)).longValue() * pontos;
        }
        if ("META".equals(tipo)) {
            return quantidade.compareTo(BigDecimal.valueOf(inteiro(regra.get("quantidade"), 0))) >= 0 ? pontos : 0;
        }
        if ("META_COMPOSTA".equals(tipo)) {
            return condicaoAgregadaAtendida(regra.get("condicao_agregada"), metricas) ? pontos : 0;
        }
        if ("FAIXAS".equals(tipo)) {
            Object faixasBrutas = regra.get("faixas");
            if (!(faixasBrutas instanceof List<?> faixas)) return 0;
            List<Map<String, Object>> ordenadas = faixas.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .sorted(Comparator.comparingLong(item -> inteiro(item.get("minimo"), 0)))
                    .toList();
            String estrategia = Objects.toString(regra.getOrDefault("estrategia", "MAIOR_FAIXA"));
            long acumulado = 0;
            long maior = 0;
            for (Map<String, Object> faixa : ordenadas) {
                if (quantidade.compareTo(BigDecimal.valueOf(inteiro(faixa.get("minimo"), 0))) >= 0) {
                    long valorFaixa = inteiro(faixa.get("pontos"), 0);
                    acumulado += valorFaixa;
                    maior = valorFaixa;
                }
            }
            return "CUMULATIVA".equalsIgnoreCase(estrategia) ? acumulado : maior;
        }
        return 0;
    }

    public List<ReferenciaMetrica> referenciasCondicaoAgregada(Object condicaoBruta) {
        List<ReferenciaMetrica> referencias = new ArrayList<>();
        coletarReferenciasCondicaoAgregada(condicaoBruta, referencias);
        return List.copyOf(new java.util.LinkedHashSet<>(referencias));
    }

    public List<String> camposCondicaoAgregada(Object condicaoBruta) {
        return referenciasCondicaoAgregada(condicaoBruta).stream()
                .map(ReferenciaMetrica::campo).distinct().toList();
    }

    private void coletarReferenciasCondicaoAgregada(Object condicaoBruta,
                                                     List<ReferenciaMetrica> referencias) {
        if (!(condicaoBruta instanceof Map<?, ?> condicao)) return;
        Object grupo = condicao.containsKey("todas") ? condicao.get("todas") : condicao.get("alguma");
        if (grupo instanceof List<?> itens) {
            itens.forEach(item -> coletarReferenciasCondicaoAgregada(item, referencias));
            return;
        }
        String campo = texto(condicao.get("campo"));
        if (campo != null) referencias.add(new ReferenciaMetrica(campo, condicao.get("quando_pessoa")));
    }

    private boolean condicaoAgregadaAtendida(Object condicaoBruta,
                                              Map<ReferenciaMetrica, BigDecimal> metricas) {
        if (!(condicaoBruta instanceof Map<?, ?> condicao)) return false;
        if (condicao.get("todas") instanceof List<?> todas) {
            return todas.stream().allMatch(item -> condicaoAgregadaAtendida(item, metricas));
        }
        if (condicao.get("alguma") instanceof List<?> alguma) {
            return alguma.stream().anyMatch(item -> condicaoAgregadaAtendida(item, metricas));
        }
        String campo = texto(condicao.get("campo"));
        BigDecimal atual = campo == null ? null : metricas.get(
                new ReferenciaMetrica(campo, condicao.get("quando_pessoa")));
        BigDecimal esperado = decimalEstrito(condicao.get("valor"));
        if (atual == null || esperado == null) return false;
        String operador = Objects.toString(condicao.get("operador"), "MAIOR_OU_IGUAL").toUpperCase(Locale.ROOT);
        int comparacao = atual.compareTo(esperado);
        return switch (operador) {
            case "IGUAL", "EQ" -> comparacao == 0;
            case "DIFERENTE", "NE" -> comparacao != 0;
            case "MAIOR_QUE", "GT" -> comparacao > 0;
            case "MAIOR_OU_IGUAL", "GTE" -> comparacao >= 0;
            case "MENOR_QUE", "LT" -> comparacao < 0;
            case "MENOR_OU_IGUAL", "LTE" -> comparacao <= 0;
            default -> false;
        };
    }

    public long calcularBonusPercentual(Map<String, Object> regra, BigDecimal numerador, BigDecimal denominador) {
        if (denominador.signum() <= 0) return 0;
        BigDecimal percentual = numerador.multiply(BigDecimal.valueOf(100))
                .divide(denominador, 8, java.math.RoundingMode.HALF_UP);
        String tipo = texto(regra.get("tipo"));
        if ("PERCENTUAL".equals(tipo)) {
            return percentual.compareTo(numero(regra.get("percentual"))) >= 0
                    ? inteiro(regra.get("pontos"), 0) : 0;
        }
        if ("FAIXAS_PERCENTUAIS".equals(tipo)) {
            Object faixasBrutas = regra.get("faixas");
            if (!(faixasBrutas instanceof List<?> faixas)) return 0;
            List<Map<String, Object>> ordenadas = faixas.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .sorted(Comparator.comparing(item -> numero(item.get("minimo_percentual"))))
                    .toList();
            String estrategia = Objects.toString(regra.getOrDefault("estrategia", "MAIOR_FAIXA"));
            long acumulado = 0;
            long maior = 0;
            for (Map<String, Object> faixa : ordenadas) {
                if (percentual.compareTo(numero(faixa.get("minimo_percentual"))) >= 0) {
                    long pontosFaixa = inteiro(faixa.get("pontos"), 0);
                    acumulado += pontosFaixa;
                    maior = pontosFaixa;
                }
            }
            return "CUMULATIVA".equalsIgnoreCase(estrategia) ? acumulado : maior;
        }
        return 0;
    }

    public String escopo(Map<String, Object> regra) {
        return Objects.toString(regra.getOrDefault("escopo", "TRIBO")).toUpperCase(Locale.ROOT);
    }

    public boolean condicaoAtendida(Object condicaoBruta, Map<String, Object> dadosRelatorio,
                                    Map<String, Object> dadosPessoa) {
        return condicaoAtendida(condicaoBruta, dadosRelatorio, dadosPessoa, "RELATORIO");
    }

    private boolean condicaoAtendida(Object condicaoBruta, Map<String, Object> dadosRelatorio,
                                     Map<String, Object> dadosPessoa, String origemPadrao) {
        if (condicaoBruta == null) return true;
        if (!(condicaoBruta instanceof Map<?, ?> condicao)) return false;
        if (condicao.get("todas") instanceof List<?> todas) {
            return todas.stream().allMatch(item ->
                    condicaoAtendida(item, dadosRelatorio, dadosPessoa, origemPadrao));
        }
        if (condicao.get("alguma") instanceof List<?> alguma) {
            return alguma.stream().anyMatch(item ->
                    condicaoAtendida(item, dadosRelatorio, dadosPessoa, origemPadrao));
        }
        String campo = texto(condicao.get("campo"));
        if (campo == null) return false;
        String origem = Objects.toString(condicao.get("origem"), origemPadrao);
        Map<String, Object> origemDados = "PESSOA".equalsIgnoreCase(origem) ? dadosPessoa : dadosRelatorio;
        Object atual = campo.equalsIgnoreCase("categoria") && "PESSOA".equalsIgnoreCase(origem)
                ? dadosPessoa.getOrDefault("categoria", "") : origemDados.get(campo);
        if (atual == null) return false;
        Object esperado = condicao.get("valor");
        String operador = Objects.toString(condicao.get("operador"), "IGUAL").toUpperCase(Locale.ROOT);
        return switch (operador) {
            case "IGUAL", "EQ" -> comparar(atual, esperado) == 0;
            case "DIFERENTE", "NE" -> comparar(atual, esperado) != 0;
            case "MAIOR_QUE", "GT" -> comparar(atual, esperado) > 0;
            case "MAIOR_OU_IGUAL", "GTE" -> comparar(atual, esperado) >= 0;
            case "MENOR_QUE", "LT" -> comparar(atual, esperado) < 0;
            case "MENOR_OU_IGUAL", "LTE" -> comparar(atual, esperado) <= 0;
            case "CONTEM" -> atual != null && Objects.toString(atual).toLowerCase(Locale.ROOT)
                    .contains(Objects.toString(esperado, "").toLowerCase(Locale.ROOT));
            default -> false;
        };
    }

    private List<Map<String, Object>> criarRegrasLegadas(DadosMissao dados) {
        List<Map<String, Object>> regras = new ArrayList<>();
        if (valor(dados.pontosPorPessoa()) > 0) regras.add(Map.of(
                "tipo", "POR_PESSOA", "categoria", "INTEGRANTE", "pontos", valor(dados.pontosPorPessoa())));
        if (valor(dados.pontosPorNovoAfastado()) > 0) regras.add(Map.of(
                "tipo", "POR_PESSOA", "categoria", "NOVO_AFASTADO", "pontos", valor(dados.pontosPorNovoAfastado())));
        if (valor(dados.bonificacaoACadaCincoNovos()) > 0) regras.add(Map.of(
                "tipo", "A_CADA", "campo", "novos_afastados", "quantidade", 5,
                "pontos", dados.bonificacaoACadaCincoNovos(), "escopo", "TRIBO"));
        if (valor(dados.metaNacional()) > 0 && valor(dados.bonificacaoMetaNacional()) > 0) regras.add(Map.of(
                "tipo", "META", "campo", "novo_ou_afastado".equals(dados.tipoPessoa())
                    ? "novos_afastados" : "integrantes",
                "quantidade", dados.metaNacional(), "pontos", dados.bonificacaoMetaNacional(),
                "escopo", "NACAO", "distribuir_para", "TRIBOS"));
        return List.copyOf(regras);
    }

    private int calcularPontuacaoLegada(Relatorio relatorio, List<PessoaPontuada> pessoas) {
        int quantidadePessoas = pessoas.isEmpty() ? relatorio.getQuantidadePessoas() : (int) pessoas.stream()
                .filter(pessoa -> !categoriaNovo(normalizarCategoria(pessoa.getCategoria()))).count();
        int quantidadeNovos = pessoas.isEmpty() ? relatorio.getQuantidadeNovosAfastados() : (int) pessoas.stream()
                .filter(pessoa -> categoriaNovo(normalizarCategoria(pessoa.getCategoria()))).count();
        long pontos = (long) quantidadePessoas * relatorio.getMissao().getPontosPorPessoa()
                + (long) quantidadeNovos * relatorio.getMissao().getPontosPorNovoAfastado()
                + (long) (quantidadeNovos / 5) * relatorio.getMissao().getBonificacaoACadaCincoNovos();
        return Math.toIntExact(pontos);
    }

    private void validarRegra(Map<String, Object> regra, String tipo) {
        switch (tipo) {
            case "POR_PESSOA" -> {
                exigirTexto(regra, "categoria");
                exigirPontuacao(regra, "pontos");
            }
            case "POR_QUANTIDADE" -> {
                exigirTexto(regra, "campo");
                exigirPontuacao(regra, "pontos_por_unidade");
            }
            case "POR_RELATORIO" -> exigirPontuacao(regra, "pontos");
            case "A_CADA" -> {
                exigirTexto(regra, "campo");
                exigirTexto(regra, "escopo");
                exigirInteiroPositivo(regra, "quantidade");
                exigirPontuacao(regra, "pontos");
            }
            case "META" -> {
                exigirTexto(regra, "campo");
                exigirTexto(regra, "escopo");
                exigirInteiroPositivo(regra, "quantidade");
                exigirPontuacao(regra, "pontos");
            }
            case "META_COMPOSTA" -> {
                exigirTexto(regra, "escopo");
                exigirPontuacao(regra, "pontos");
                validarCondicaoAgregada(regra.get("condicao_agregada"), 0);
            }
            case "FAIXAS" -> {
                exigirTexto(regra, "campo");
                exigirTexto(regra, "escopo");
                if (!(regra.get("faixas") instanceof List<?> faixas) || faixas.isEmpty() || faixas.size() > 30)
                    throw erro("A regra de faixas precisa ter de uma a 30 faixas.");
                for (Object faixa : faixas) {
                    if (!(faixa instanceof Map<?, ?> mapa)
                            || inteiro(mapa.get("minimo"), -1) < 0 || !pontuacaoValida(mapa.get("pontos"))) {
                        throw erro("Cada faixa precisa informar um mínimo não negativo e pontos entre -1.000.000 e 1.000.000.");
                    }
                }
                String estrategia = Objects.toString(regra.getOrDefault("estrategia", "MAIOR_FAIXA"))
                        .toUpperCase(Locale.ROOT);
                if (!List.of("MAIOR_FAIXA", "CUMULATIVA").contains(estrategia))
                    throw erro("A estratégia das faixas deve ser MAIOR_FAIXA ou CUMULATIVA.");
            }
            case "PERCENTUAL" -> {
                exigirTexto(regra, "campo");
                exigirTexto(regra, "campo_base");
                BigDecimal percentual = decimalEstrito(regra.get("percentual"));
                if (percentual == null || percentual.signum() < 0
                        || percentual.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw erro("O percentual precisa estar entre 0 e 100.");
                }
                exigirPontuacao(regra, "pontos");
            }
            case "FAIXAS_PERCENTUAIS" -> {
                exigirTexto(regra, "campo");
                exigirTexto(regra, "campo_base");
                if (!(regra.get("faixas") instanceof List<?> faixas) || faixas.isEmpty() || faixas.size() > 30)
                    throw erro("A regra de faixas percentuais precisa ter de uma a 30 faixas.");
                java.util.Set<BigDecimal> percentuais = new java.util.HashSet<>();
                for (Object faixa : faixas) {
                    if (!(faixa instanceof Map<?, ?> mapa))
                        throw erro("Cada faixa precisa informar percentual mínimo e pontos entre -1.000.000 e 1.000.000.");
                    BigDecimal minimo = decimalEstrito(mapa.get("minimo_percentual"));
                    long pontos = inteiro(mapa.get("pontos"), -1);
                    if (minimo == null || minimo.signum() < 0
                            || minimo.compareTo(BigDecimal.valueOf(100)) > 0 || !pontuacaoValida(pontos)
                            || !percentuais.add(minimo.stripTrailingZeros())) {
                        throw erro("Cada faixa precisa ter um percentual mínimo único entre 0 e 100 e pontos entre -1.000.000 e 1.000.000.");
                    }
                }
                String estrategia = Objects.toString(regra.getOrDefault("estrategia", "MAIOR_FAIXA"))
                        .toUpperCase(Locale.ROOT);
                if (!List.of("MAIOR_FAIXA", "CUMULATIVA").contains(estrategia))
                    throw erro("A estratégia das faixas percentuais deve ser MAIOR_FAIXA ou CUMULATIVA.");
            }
            default -> throw erro("Tipo de regra não suportado.");
        }
        String escopo = Objects.toString(regra.getOrDefault("escopo", "TRIBO"), "TRIBO").toUpperCase(Locale.ROOT);
        if (!List.of("TRIBO", "NACAO").contains(escopo)) throw erro("O escopo precisa ser TRIBO ou NACAO.");
        if (regra.containsKey("quando") && !(regra.get("quando") instanceof Map<?, ?>)) {
            throw erro("A condição da regra precisa ser um objeto.");
        }
        validarCondicao(regra.get("quando"));
        if (!"POR_PESSOA".equals(tipo)) validarOrigemQuandoInformada(regra.get("quando"), "RELATORIO");
        if (regra.containsKey("quando_pessoa")) {
            if (!List.of("POR_QUANTIDADE", "A_CADA", "META", "FAIXAS", "PERCENTUAL", "FAIXAS_PERCENTUAIS").contains(tipo))
                throw erro("A condição por pessoa só pode ser usada com regras de quantidade ou bônus agregados.");
            if (!camposDeContagem().contains(Objects.toString(regra.get("campo"), "").toLowerCase(Locale.ROOT))) {
                throw erro("A condição por pessoa exige um campo de contagem de pessoas.");
            }
            validarCondicao(regra.get("quando_pessoa"));
            validarOrigemQuandoInformada(regra.get("quando_pessoa"), "PESSOA");
        }
        String distribuir = Objects.toString(regra.get("distribuir_para"), "TRIBOS").toUpperCase(Locale.ROOT);
        if (!List.of("TRIBOS", "TRIBO_GERADORA").contains(distribuir)) {
            throw erro("A distribuição do bônus deve ser TRIBOS ou TRIBO_GERADORA.");
        }
    }

    private void validarCondicaoAgregada(Object valor, int profundidade) {
        validarCondicaoAgregada(valor, profundidade, new int[] {0});
    }

    private void validarCondicaoAgregada(Object valor, int profundidade, int[] nosVisitados) {
        if (profundidade > 8)
            throw erro("A profundidade da meta composta não pode passar do nível oito, contando a raiz como nível zero.");
        if (++nosVisitados[0] > 200)
            throw erro("A meta composta pode ter no máximo 200 condições.");
        if (!(valor instanceof Map<?, ?> condicao))
            throw erro("A meta composta precisa informar uma condição agregada válida.");
        boolean todas = condicao.get("todas") instanceof List<?>;
        boolean alguma = condicao.get("alguma") instanceof List<?>;
        if (todas || alguma) {
            if (todas == alguma) throw erro("Use 'todas' ou 'alguma' em cada grupo da meta composta.");
            List<?> itens = (List<?>) condicao.get(todas ? "todas" : "alguma");
            if (itens.isEmpty() || itens.size() > 30)
                throw erro("Cada grupo da meta composta precisa ter de um a 30 condições.");
            itens.forEach(item -> validarCondicaoAgregada(item, profundidade + 1, nosVisitados));
            return;
        }
        exigirTextoCondicao(condicao, "campo");
        BigDecimal esperado = decimalEstrito(condicao.get("valor"));
        if (esperado == null || esperado.signum() < 0 || esperado.compareTo(BigDecimal.valueOf(1_000_000)) > 0)
            throw erro("Cada condição agregada precisa ter um valor numérico entre 0 e 1.000.000.");
        String operador = Objects.toString(condicao.get("operador"), "MAIOR_OU_IGUAL").toUpperCase(Locale.ROOT);
        if (!List.of("IGUAL", "EQ", "DIFERENTE", "NE", "MAIOR_QUE", "GT", "MAIOR_OU_IGUAL", "GTE",
                "MENOR_QUE", "LT", "MENOR_OU_IGUAL", "LTE").contains(operador)) {
            throw erro("Operador não suportado na condição agregada: " + operador + ".");
        }
        if (condicao.containsKey("quando_pessoa")) {
            if (!camposDeContagem().contains(Objects.toString(condicao.get("campo"), "").toLowerCase(Locale.ROOT)))
                throw erro("A condição por pessoa só pode filtrar uma contagem de pessoas.");
            validarCondicao(condicao.get("quando_pessoa"));
            validarOrigemQuandoInformada(condicao.get("quando_pessoa"), "PESSOA");
        }
    }

    private void exigirTextoCondicao(Map<?, ?> condicao, String chave) {
        if (texto(condicao.get(chave)) == null)
            throw erro("Cada condição agregada precisa informar " + chave + ".");
    }

    private void validarCondicao(Object valor) {
        if (valor == null) return;
        if (!(valor instanceof Map<?, ?> condicao)) throw erro("A condição da regra precisa ser um objeto.");
        if (condicao.get("todas") instanceof List<?> todas) {
            if (todas.isEmpty() || todas.size() > 30) throw erro("A condição 'todas' precisa ter de uma a 30 itens.");
            todas.forEach(this::validarCondicao);
            return;
        }
        if (condicao.get("alguma") instanceof List<?> alguma) {
            if (alguma.isEmpty() || alguma.size() > 30) throw erro("A condição 'alguma' precisa ter de uma a 30 itens.");
            alguma.forEach(this::validarCondicao);
            return;
        }
        if (texto(condicao.get("campo")) == null || !condicao.containsKey("valor") || condicao.get("valor") == null)
            throw erro("Cada condição precisa indicar um campo e um valor.");
        String operador = Objects.toString(condicao.get("operador"), "IGUAL").toUpperCase(Locale.ROOT);
        if (!List.of("IGUAL", "EQ", "DIFERENTE", "NE", "MAIOR_QUE", "GT", "MAIOR_OU_IGUAL", "GTE",
                "MENOR_QUE", "LT", "MENOR_OU_IGUAL", "LTE", "CONTEM").contains(operador)) {
            throw erro("Operador de condição não suportado: " + operador + ".");
        }
        String origem = Objects.toString(condicao.get("origem"), "RELATORIO").toUpperCase(Locale.ROOT);
        if (!List.of("RELATORIO", "PESSOA").contains(origem)) throw erro("A origem da condição precisa ser RELATORIO ou PESSOA.");
    }

    private void validarOrigemQuandoInformada(Object valor, String origemEsperada) {
        if (!(valor instanceof Map<?, ?> condicao)) return;
        if (condicao.get("todas") instanceof List<?> todas) {
            todas.forEach(item -> validarOrigemQuandoInformada(item, origemEsperada));
            return;
        }
        if (condicao.get("alguma") instanceof List<?> alguma) {
            alguma.forEach(item -> validarOrigemQuandoInformada(item, origemEsperada));
            return;
        }
        Object origem = condicao.get("origem");
        if (origem != null && !origemEsperada.equalsIgnoreCase(origem.toString())) {
            throw erro("A condição desta regra precisa usar dados de " + origemEsperada + ".");
        }
    }

    private void exigirTexto(Map<String, Object> regra, String chave) {
        if (texto(regra.get(chave)) == null) throw erro("A regra precisa informar " + chave + ".");
    }

    private void exigirPontuacao(Map<String, Object> regra, String chave) {
        long valor = inteiro(regra.get(chave), -1);
        if (!pontuacaoValida(valor)) throw erro("A pontuação de cada regra deve estar entre -1.000.000 e 1.000.000.");
    }

    private boolean pontuacaoValida(Object valor) {
        long pontos = inteiro(valor, Long.MIN_VALUE);
        return pontos >= -1_000_000 && pontos <= 1_000_000;
    }

    private void exigirInteiroPositivo(Map<String, Object> regra, String chave) {
        long valor = inteiro(regra.get(chave), -1);
        if (valor <= 0 || valor > 1_000_000) throw erro("O valor de " + chave + " deve estar entre 1 e 1.000.000.");
    }

    private static BigDecimal numero(Object valor) {
        BigDecimal decimal = decimalEstrito(valor);
        return decimal == null ? BigDecimal.ZERO : decimal;
    }

    private static BigDecimal decimalEstrito(Object valor) {
        if (valor instanceof Number || valor instanceof String) {
            try { return new BigDecimal(valor.toString().trim().replace(',', '.')); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private static int comparar(Object atual, Object esperado) {
        if (atual == null || esperado == null) return Objects.equals(atual, esperado) ? 0 : -1;
        BigDecimal atualNumero = numero(atual);
        BigDecimal esperadoNumero = numero(esperado);
        if (pareceNumero(atual) && pareceNumero(esperado)) return atualNumero.compareTo(esperadoNumero);
        return Objects.toString(atual).equalsIgnoreCase(Objects.toString(esperado)) ? 0 :
                Objects.toString(atual).compareToIgnoreCase(Objects.toString(esperado));
    }

    private static boolean pareceNumero(Object valor) {
        if (valor instanceof Number) return true;
        if (valor instanceof String texto) {
            try { new BigDecimal(texto.trim().replace(',', '.')); return true; }
            catch (NumberFormatException ignored) { return false; }
        }
        return false;
    }

    private static String normalizarCategoria(String categoria) {
        if (categoria == null) return "";
        String normalizada = categoria.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (normalizada.equals("NOVO") || normalizada.equals("AFASTADO") || normalizada.equals("NOVO_AFASTADO"))
            return "NOVO_AFASTADO";
        if (normalizada.equals("JOVEM_FIRME")) return "INTEGRANTE";
        return normalizada;
    }

    private static boolean correspondeCategoria(String regra, String pessoa) {
        String categoriaPessoa = normalizarCategoria(pessoa);
        return "NOVO_AFASTADO".equals(regra) ? categoriaNovo(categoriaPessoa) : regra.equals(categoriaPessoa);
    }

    private static boolean categoriaNovo(String categoria) {
        return "NOVO_AFASTADO".equals(categoria) || categoria.startsWith("NOVO_AFASTADO_")
                || categoria.startsWith("NOVO_") || categoria.startsWith("AFASTADO_");
    }

    private static List<String> camposDeContagem() {
        return List.of("integrantes", "pessoas", "total_pessoas", "novos_afastados", "novos", "afastados", "convidados");
    }

    private static long inteiro(Object valor, long padrao) {
        if (valor instanceof Number numero) {
            try { return new BigDecimal(numero.toString()).longValueExact(); }
            catch (NumberFormatException | ArithmeticException ignored) { return padrao; }
        }
        if (valor instanceof String texto) {
            try { return Long.parseLong(texto); }
            catch (NumberFormatException ignored) { return padrao; }
        }
        return padrao;
    }

    private static int valor(Integer valor) { return valor == null ? 0 : valor; }

    private static String texto(Object valor) {
        if (valor == null) return null;
        String texto = valor.toString().trim();
        return texto.isEmpty() ? null : texto;
    }

    private static ResponseStatusException erro(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
