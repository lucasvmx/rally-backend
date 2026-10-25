package br.org.fju.rally.servico;

import br.org.fju.rally.api.DadosMissao;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ValidacaoConfiguracaoMissaoService {
    public record Configuracao(List<Map<String, Object>> regrasPontuacao,
                               List<Map<String, Object>> camposRelatorio) {}

    private final RegrasPontuacaoService regrasPontuacao;

    public ValidacaoConfiguracaoMissaoService(RegrasPontuacaoService regrasPontuacao) {
        this.regrasPontuacao = regrasPontuacao;
    }

    public Configuracao validar(DadosMissao dados) {
        List<Map<String, Object>> regras = regrasPontuacao.normalizar(dados.regrasPontuacao(), dados);
        List<Map<String, Object>> campos = validarCampos(dados.camposRelatorio());
        validarCamposDasRegras(regras, campos);
        return new Configuracao(regras, campos);
    }

    private List<Map<String, Object>> validarCampos(List<Map<String, Object>> campos) {
        if (campos == null) return List.of();
        if (campos.size() > 30) throw erro("A missão pode ter no máximo 30 campos adicionais.");
        Set<String> ids = new HashSet<>();
        List<Map<String, Object>> normalizados = new ArrayList<>();
        for (Map<String, Object> campo : campos) {
            if (campo == null) throw erro("Confira os campos adicionais da missão: há um campo vazio.");
            Object id = campo.get("id");
            Object titulo = campo.get("titulo");
            Object tipo = campo.get("tipo");
            Object escopo = campo.getOrDefault("escopo", "RELATORIO");
            Object obrigatorio = campo.getOrDefault("obrigatorio", false);
            if (!(id instanceof String idTexto) || idTexto.isBlank() || !ids.add(idTexto)
                    || idTexto.length() > 80
                    || !(titulo instanceof String tituloTexto) || tituloTexto.isBlank()
                    || tituloTexto.length() > 200
                    || !(tipo instanceof String tipoTexto)
                    || !List.of("texto", "paragrafo", "numero", "selecao").contains(tipoTexto)
                    || !(escopo instanceof String escopoTexto)
                    || !(obrigatorio instanceof Boolean)
                    || !List.of("RELATORIO", "PESSOA").contains(escopoTexto.toUpperCase(Locale.ROOT))
                    || ("categoria".equalsIgnoreCase(idTexto) && "PESSOA".equalsIgnoreCase(escopoTexto))) {
                throw erro("Confira os campos adicionais da missão: identificadores, títulos e tipos precisam ser válidos e únicos.");
            }
            if ("selecao".equals(tipoTexto)
                    && (!(campo.get("opcoes") instanceof List<?> opcoes) || opcoes.isEmpty() || opcoes.size() > 50
                    || opcoes.stream().anyMatch(opcao -> !(opcao instanceof String texto)
                    || texto.isBlank() || texto.length() > 200))) {
                throw erro("Campos de seleção precisam ter de uma a 50 opções válidas.");
            }
            Map<String, Object> campoNormalizado = new java.util.LinkedHashMap<>(campo);
            campoNormalizado.put("escopo", escopoTexto.toUpperCase(Locale.ROOT));
            campoNormalizado.put("obrigatorio", obrigatorio);
            normalizados.add(Collections.unmodifiableMap(campoNormalizado));
        }
        return List.copyOf(normalizados);
    }

    private void validarCamposDasRegras(List<Map<String, Object>> regras, List<Map<String, Object>> campos) {
        Set<String> contagens = Set.of("integrantes", "pessoas", "total_pessoas",
                "novos_afastados", "novos", "afastados", "convidados");
        Map<String, Map<String, Object>> porId = new HashMap<>();
        Set<String> camposRelatorio = new HashSet<>();
        Set<String> camposPessoa = new HashSet<>();
        campos.forEach(campo -> porId.put((String) campo.get("id"), campo));
        campos.forEach(campo -> {
            if ("PESSOA".equals(campo.get("escopo"))) camposPessoa.add((String) campo.get("id"));
            else camposRelatorio.add((String) campo.get("id"));
        });
        for (Map<String, Object> regra : regras) {
            String tipo = java.util.Objects.toString(regra.get("tipo"), "");
            validarCondicoesDaRegra(regra.get("quando"), camposRelatorio, camposPessoa,
                    "POR_PESSOA".equals(tipo) ? "PESSOA" : "RELATORIO");
            validarCondicoesDaRegra(regra.get("quando_pessoa"), camposRelatorio, camposPessoa, "PESSOA");
            if ("META_COMPOSTA".equals(tipo)) {
                for (RegrasPontuacaoService.ReferenciaMetrica referencia :
                        regrasPontuacao.referenciasCondicaoAgregada(regra.get("condicao_agregada"))) {
                    validarCampoNumerico(referencia.campo(), contagens, porId);
                    validarCondicoesDaRegra(referencia.quandoPessoa(), camposRelatorio, camposPessoa, "PESSOA");
                }
                continue;
            }
            if (!List.of("POR_QUANTIDADE", "A_CADA", "META", "FAIXAS", "PERCENTUAL", "FAIXAS_PERCENTUAIS")
                    .contains(tipo)) continue;
            validarCampoNumerico(texto(regra.get("campo")), contagens, porId);
            if (tipo.equals("PERCENTUAL") || tipo.equals("FAIXAS_PERCENTUAIS")) {
                validarCampoNumerico(texto(regra.get("campo_base")), contagens, porId);
            }
        }
    }

    private void validarCondicoesDaRegra(Object valor, Set<String> camposRelatorio,
                                         Set<String> camposPessoa, String origemPadrao) {
        if (!(valor instanceof Map<?, ?> condicao)) return;
        Object lista = condicao.get("todas");
        if (!(lista instanceof List<?>)) lista = condicao.get("alguma");
        if (lista instanceof List<?> condicoes) {
            condicoes.forEach(item -> validarCondicoesDaRegra(item, camposRelatorio, camposPessoa, origemPadrao));
            return;
        }
        String id = texto(condicao.get("campo"));
        String origem = java.util.Objects.toString(condicao.get("origem"), origemPadrao).toUpperCase(Locale.ROOT);
        boolean conhecido = "PESSOA".equals(origem)
                ? "categoria".equalsIgnoreCase(id) || camposPessoa.contains(id)
                : camposRelatorio.contains(id);
        if (!conhecido) throw erro("Uma condição da regra usa um campo que não foi definido no escopo informado.");
    }

    private void validarCampoNumerico(String id, Set<String> contagens,
                                      Map<String, Map<String, Object>> campos) {
        if (id == null) throw erro("Toda regra de quantidade precisa indicar um campo numérico.");
        if (contagens.contains(id.toLowerCase(Locale.ROOT))) return;
        Map<String, Object> campo = campos.get(id);
        if (campo == null || !"numero".equals(campo.get("tipo"))
                || !"RELATORIO".equalsIgnoreCase(java.util.Objects.toString(campo.get("escopo"), "RELATORIO"))) {
            throw erro("As regras quantitativas precisam usar contagens conhecidas ou campos numéricos do relatório.");
        }
    }

    private static String texto(Object valor) {
        return valor instanceof String texto && !texto.isBlank() ? texto : null;
    }

    private static ResponseStatusException erro(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
