package br.org.fju.rally.servico;

import br.org.fju.rally.api.DadosMissao;
import br.org.fju.rally.api.DadosRally;
import br.org.fju.rally.api.RequisicaoCriarMissoes;
import br.org.fju.rally.api.RequisicaoInterpretacao;
import br.org.fju.rally.api.RespostaInterpretacao;
import br.org.fju.rally.api.RespostaInterpretacaoMissoes;
import br.org.fju.rally.api.RespostaMissao;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.Rally;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.RallyRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;

@Service
public class MissaoService {
    private final MissaoRepositorio missoes;
    private final RallyRepositorio rallies;
    private final InterpretadorDeRegras interpretador;
    private final AcessoService acesso;
    private final RegrasPontuacaoService regrasPontuacao;

    public MissaoService(MissaoRepositorio missoes, RallyRepositorio rallies,
                         InterpretadorDeRegras interpretador, AcessoService acesso,
                         RegrasPontuacaoService regrasPontuacao) {
        this.missoes = missoes;
        this.rallies = rallies;
        this.interpretador = interpretador;
        this.acesso = acesso;
        this.regrasPontuacao = regrasPontuacao;
    }

    public RespostaInterpretacaoMissoes interpretar(RequisicaoInterpretacao corpo, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        return interpretador.interpretarMissoes(corpo.texto());
    }

    public RespostaInterpretacao interpretarRegras(RequisicaoInterpretacao corpo, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        return interpretador.interpretar(corpo.texto());
    }

    @Transactional
    public List<RespostaMissao> criar(RequisicaoCriarMissoes corpo, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        DadosRally dadosRally = obterDadosRally(corpo);
        validarCamposRally(dadosRally, corpo.missoes());
        Rally rally = rallies.saveAndFlush(new Rally(UUID.randomUUID(), dadosRally.nome().trim(),
                dadosRally.dataInicio(), dadosRally.dataFim()));
        List<Missao> novas = corpo.missoes().stream().map(dados -> converter(dados, rally)).toList();
        List<Missao> salvas = missoes.saveAllAndFlush(novas);
        return salvas.stream().map(RespostaMissao::de).toList();
    }

    private Missao converter(DadosMissao dados, Rally rally) {
        if (dados.dataInicio() == null || dados.dataFim() == null) {
            throw erro("Informe as datas de início e fim de todas as missões.");
        }
        if (dados.dataInicio().isAfter(dados.dataFim())) {
            throw erro("A data de início precisa ser anterior ou igual à data de fim.");
        }
        String link = dados.linkGoogleForms() == null || dados.linkGoogleForms().isBlank()
                ? null : dados.linkGoogleForms().trim();
        if (link != null && !InterpretadorDeRegras.linkGoogleFormsValido(link)) {
            throw erro("Use um link HTTPS do Google Forms válido.");
        }
        List<Map<String, Object>> regras = regrasPontuacao.normalizar(dados.regrasPontuacao(), dados);
        List<Map<String, Object>> campos = validarCampos(dados.camposRelatorio());
        validarCamposDasRegras(regras, campos);
        return new Missao(UUID.randomUUID(), dados.nome().trim(), dados.descricao(), dados.dataInicio(), dados.dataFim(),
                dados.prazoEnvio(), valor(dados.pontosPorPessoa()), valor(dados.pontosPorNovoAfastado()),
                valor(dados.bonificacaoACadaCincoNovos()), valor(dados.metaNacional()),
                valor(dados.bonificacaoMetaNacional()), dados.exigeFoto(),
                dados.tipoPessoa() == null ? "integrante" : dados.tipoPessoa(), link,
                "PUBLICADA", 1, rally, regras, campos);
    }

    private DadosRally obterDadosRally(RequisicaoCriarMissoes corpo) {
        if (corpo.rally() != null) return corpo.rally();
        LocalDate inicio = corpo.missoes().stream().map(DadosMissao::dataInicio)
                .filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        LocalDate fim = corpo.missoes().stream().map(DadosMissao::dataFim)
                .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        if (inicio == null || fim == null) throw erro("Informe as datas do rally.");
        return new DadosRally("Rally " + inicio + " a " + fim, inicio, fim);
    }

    private void validarCamposRally(DadosRally rally, List<DadosMissao> missoes) {
        if (rally.nome() == null || rally.nome().isBlank() || rally.dataInicio() == null || rally.dataFim() == null
                || rally.dataInicio().isAfter(rally.dataFim())) {
            throw erro("Informe o nome e um período válido para o rally.");
        }
        boolean sobreposto = rallies.findAll().stream().anyMatch(existente ->
                !rally.dataFim().isBefore(existente.getDataInicio())
                        && !rally.dataInicio().isAfter(existente.getDataFim()));
        if (sobreposto) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "O período informado coincide com outro rally cadastrado.");
        for (DadosMissao missao : missoes) {
            if (missao.dataInicio() == null || missao.dataFim() == null
                    || missao.dataInicio().isBefore(rally.dataInicio()) || missao.dataFim().isAfter(rally.dataFim())) {
                throw erro("As datas de cada missão precisam estar dentro do período do rally.");
            }
        }
    }

    private List<Map<String, Object>> validarCampos(List<Map<String, Object>> campos) {
        if (campos == null) return List.of();
        if (campos.size() > 30) throw erro("A missão pode ter no máximo 30 campos adicionais.");
        java.util.Set<String> ids = new java.util.HashSet<>();
        List<Map<String, Object>> normalizados = new java.util.ArrayList<>();
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
                    || !List.of("RELATORIO", "PESSOA").contains(escopoTexto.toUpperCase(java.util.Locale.ROOT))
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
            campoNormalizado.put("escopo", escopoTexto.toUpperCase(java.util.Locale.ROOT));
            campoNormalizado.put("obrigatorio", obrigatorio);
            normalizados.add(java.util.Collections.unmodifiableMap(campoNormalizado));
        }
        return List.copyOf(normalizados);
    }

    private void validarCamposDasRegras(List<Map<String, Object>> regras, List<Map<String, Object>> campos) {
        java.util.Set<String> contagens = java.util.Set.of("integrantes", "pessoas", "total_pessoas",
                "novos_afastados", "novos", "afastados", "convidados");
        Map<String, Map<String, Object>> porId = new java.util.HashMap<>();
        java.util.Set<String> camposRelatorio = new java.util.HashSet<>();
        java.util.Set<String> camposPessoa = new java.util.HashSet<>();
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
            if (!List.of("POR_QUANTIDADE", "A_CADA", "META", "FAIXAS", "PERCENTUAL", "FAIXAS_PERCENTUAIS")
                    .contains(tipo)) continue;
            validarCampoNumerico(texto(regra.get("campo")), contagens, porId);
            if (tipo.equals("PERCENTUAL") || tipo.equals("FAIXAS_PERCENTUAIS")) {
                validarCampoNumerico(texto(regra.get("campo_base")), contagens, porId);
            }
        }
    }

    private void validarCondicoesDaRegra(Object valor, java.util.Set<String> camposRelatorio,
                                         java.util.Set<String> camposPessoa, String origemPadrao) {
        if (!(valor instanceof Map<?, ?> condicao)) return;
        Object lista = condicao.get("todas");
        if (!(lista instanceof List<?>)) lista = condicao.get("alguma");
        if (lista instanceof List<?> condicoes) {
            condicoes.forEach(item -> validarCondicoesDaRegra(item, camposRelatorio, camposPessoa, origemPadrao));
            return;
        }
        String id = texto(condicao.get("campo"));
        String origem = java.util.Objects.toString(condicao.get("origem"), origemPadrao).toUpperCase(java.util.Locale.ROOT);
        boolean conhecido = "PESSOA".equals(origem)
                ? "categoria".equalsIgnoreCase(id) || camposPessoa.contains(id)
                : camposRelatorio.contains(id);
        if (!conhecido) throw erro("Uma condição da regra usa um campo que não foi definido no escopo informado.");
    }

    private void validarCampoNumerico(String id, java.util.Set<String> contagens,
                                      Map<String, Map<String, Object>> campos) {
        if (id == null) throw erro("Toda regra de quantidade precisa indicar um campo numérico.");
        if (contagens.contains(id.toLowerCase(java.util.Locale.ROOT))) return;
        Map<String, Object> campo = campos.get(id);
        if (campo == null || !"numero".equals(campo.get("tipo"))
                || !"RELATORIO".equalsIgnoreCase(java.util.Objects.toString(campo.get("escopo"), "RELATORIO"))) {
            throw erro("As regras quantitativas precisam usar contagens conhecidas ou campos numéricos do relatório.");
        }
    }

    private static String texto(Object valor) {
        return valor instanceof String texto && !texto.isBlank() ? texto : null;
    }

    private static int valor(Integer valor) { return valor == null ? 0 : valor; }

    private static ResponseStatusException erro(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
