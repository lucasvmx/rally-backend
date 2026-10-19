package br.org.fju.rally.servico;

import br.org.fju.rally.api.PessoaRelatorio;
import br.org.fju.rally.api.RequisicaoRelatorio;
import br.org.fju.rally.api.RespostaRelatorio;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.PessoaPontuada;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.dominio.Tribo;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.PessoaPontuadaRepositorio;
import br.org.fju.rally.repositorio.RelatorioRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class RelatorioService {
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    private final RelatorioRepositorio relatorioRepositorio;
    private final MissaoRepositorio missaoRepositorio;
    private final TriboRepositorio triboRepositorio;
    private final PessoaPontuadaRepositorio pessoaRepositorio;
    private final PontuacaoService pontuacaoService;
    private final AcessoService acessoService;
    private final Clock relogio;

    public RelatorioService(RelatorioRepositorio relatorioRepositorio,
                            MissaoRepositorio missaoRepositorio,
                            TriboRepositorio triboRepositorio,
                            PessoaPontuadaRepositorio pessoaRepositorio,
                            PontuacaoService pontuacaoService,
                            AcessoService acessoService) {
        this.relatorioRepositorio = relatorioRepositorio;
        this.missaoRepositorio = missaoRepositorio;
        this.triboRepositorio = triboRepositorio;
        this.pessoaRepositorio = pessoaRepositorio;
        this.pontuacaoService = pontuacaoService;
        this.acessoService = acessoService;
        this.relogio = Clock.systemDefaultZone();
    }

    @Transactional(readOnly = true)
    public List<RespostaRelatorio> listar(HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso acesso = acessoService.contexto(requisicao);
        return relatorioRepositorio.buscarComReferencias().stream()
                .filter(relatorio -> acesso.podeAcessarTribo(relatorio.getTribo().getId(),
                        relatorio.getTribo().getNacao().getId()))
                .map(RespostaRelatorio::de)
                .toList();
    }

    @Transactional
    public RespostaRelatorio criar(RequisicaoRelatorio corpo, HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso acesso = acessoService.exigirCoordenador(requisicao);
        Tribo tribo = triboRepositorio.findById(corpo.triboId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tribo não encontrada."));
        if (!acesso.podeAcessarTribo(tribo.getId(), tribo.getNacao().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O coordenador só pode enviar relatório para sua tribo ou nação.");
        }
        return criarPublico(corpo, false);
    }

    @Transactional
    public RespostaRelatorio criarPublico(RequisicaoRelatorio corpo) {
        return criarPublico(corpo, false);
    }

    @Transactional
    public RespostaRelatorio criarPublico(RequisicaoRelatorio corpo, boolean fotoValidadaNoEnvio) {
        Missao missao = missaoRepositorio.buscarParaEnvio(corpo.missaoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Missão não encontrada."));
        Tribo tribo = triboRepositorio.findById(corpo.triboId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tribo não encontrada."));
        if (missao.isExigeFoto() && !fotoValidadaNoEnvio) {
            throw erro("Esta missão exige foto; envie o relatório pelo formulário com a foto incluída.");
        }

        List<PessoaRelatorio> pessoasDeclaradas = corpo.pessoas() == null ? List.of() : corpo.pessoas();
        validarQuantidadeDeclarada(corpo, pessoasDeclaradas);
        Map<String, Object> dados = validarDados(corpo.dados());
        UUID relatorioId = UUID.randomUUID();
        List<Map<String, Object>> avisos = new ArrayList<>();
        List<PessoaRascunho> rascunhos = resolverPessoas(missao, pessoasDeclaradas, avisos);
        validarCamposDaMissao(missao, dados, rascunhos);

        int integrantesAceitos = (int) rascunhos.stream()
                .filter(pessoa -> !categoriaNovo(pessoa.categoria())).count();
        int novosAceitos = (int) rascunhos.stream()
                .filter(pessoa -> categoriaNovo(pessoa.categoria())).count();
        dados.put("quantidade_pessoas_declarada", corpo.quantidadePessoas());
        dados.put("quantidade_novos_afastados_declarada", corpo.quantidadeNovosAfastados());

        LocalDateTime agora = LocalDateTime.now(relogio);
        boolean atrasado = missao.getPrazoEnvio() != null && agora.isAfter(missao.getPrazoEnvio());
        Relatorio relatorio = new Relatorio(relatorioId, missao, tribo, integrantesAceitos, novosAceitos,
                corpo.observacoes(), agora, atrasado, "ENVIADO", 0, null, dados, avisos);
        relatorioRepositorio.saveAndFlush(relatorio);
        List<PessoaPontuada> novasPessoas = rascunhos.stream().map(pessoa -> new PessoaPontuada(
                UUID.randomUUID(), missao, relatorio, pessoa.nome(), pessoa.nomeNormalizado(),
                pessoa.ocorrencia(), pessoa.categoria(), pessoa.dados())).toList();
        if (!novasPessoas.isEmpty()) pessoaRepositorio.saveAllAndFlush(novasPessoas);
        return RespostaRelatorio.de(relatorio);
    }

    @Transactional
    public RespostaRelatorio aprovar(UUID id, String justificativa, HttpServletRequest requisicao) {
        acessoService.exigirAssistente(requisicao);
        Relatorio relatorio = buscar(id);
        exigirEnviado(relatorio);
        relatorio.aprovar(pontuacaoService.calcularPontos(relatorio), justificativa);
        return RespostaRelatorio.de(relatorioRepositorio.save(relatorio));
    }

    @Transactional
    public RespostaRelatorio rejeitar(UUID id, String justificativa, HttpServletRequest requisicao) {
        acessoService.exigirAssistente(requisicao);
        Relatorio relatorio = buscar(id);
        exigirEnviado(relatorio);
        relatorio.rejeitar(justificativa);
        return RespostaRelatorio.de(relatorioRepositorio.save(relatorio));
    }

    private List<PessoaRascunho> resolverPessoas(Missao missao, List<PessoaRelatorio> declaradas,
                                                  List<Map<String, Object>> avisos) {
        Map<String, List<PessoaRelatorio>> porNome = new LinkedHashMap<>();
        for (PessoaRelatorio declarada : declaradas) {
            String nome = nomeValido(declarada.nome());
            String nomeNormalizado = normalizarNome(nome);
            String categoria = categoria(declarada.categoria());
            if (!categoriasPermitidas(missao).contains(categoria))
                throw erro("A categoria informada não está configurada para esta missão.");
            Map<String, Object> dadosPessoa = validarDados(declarada.dados());
            dadosPessoa.put("categoria", categoria);
            porNome.computeIfAbsent(nomeNormalizado, ignorado -> new ArrayList<>())
                    .add(new PessoaRelatorio(nome, categoria, dadosPessoa));
        }

        List<PessoaRascunho> resultado = new ArrayList<>();
        for (Map.Entry<String, List<PessoaRelatorio>> grupo : porNome.entrySet()) {
            List<PessoaPontuada> existentes = pessoaRepositorio.findByMissaoIdAndNomeNormalizado(
                    missao.getId(), grupo.getKey());
            int proximaOcorrencia = existentes.stream().mapToInt(PessoaPontuada::getOcorrencia).max().orElse(0) + 1;
            if (grupo.getValue().size() > 1 || !existentes.isEmpty()) {
                avisos.add(aviso("NOME_REPETIDO_NA_MISSAO",
                        "Um nome repetido nesta missão foi considerado como pessoa diferente. Confira possíveis homônimos; a identificação usa somente o nome."));
            }
            for (int indice = 0; indice < grupo.getValue().size(); indice++) {
                PessoaRelatorio pessoa = grupo.getValue().get(indice);
                resultado.add(new PessoaRascunho(pessoa.nome(), grupo.getKey(),
                        proximaOcorrencia + indice, pessoa.categoria(), pessoa.dados()));
            }
        }
        return resultado;
    }

    private static java.util.Set<String> categoriasPermitidas(Missao missao) {
        java.util.Set<String> permitidas = new java.util.HashSet<>(java.util.Set.of(
                "INTEGRANTE", "NOVO_AFASTADO", "CONVIDADO"));
        List<Map<String, Object>> regras = missao.getRegrasPontuacao();
        if (regras == null) return permitidas;
        for (Map<String, Object> regra : regras) {
            if ("POR_PESSOA".equals(regra.get("tipo")) && regra.get("categoria") instanceof String categoria) {
                permitidas.add(categoria(categoria));
            }
        }
        return permitidas;
    }

    private void validarQuantidadeDeclarada(RequisicaoRelatorio corpo, List<PessoaRelatorio> pessoas) {
        if ((corpo.quantidadePessoas() > 0 || corpo.quantidadeNovosAfastados() > 0) && pessoas.isEmpty()) {
            throw erro("Informe o nome de cada pessoa para impedir pontuação repetida na mesma missão.");
        }
        long integrantes = pessoas.stream().filter(pessoa -> !categoriaNovo(categoria(pessoa.categoria()))).count();
        long novos = pessoas.stream().filter(pessoa -> categoriaNovo(categoria(pessoa.categoria()))).count();
        if (integrantes != corpo.quantidadePessoas() || novos != corpo.quantidadeNovosAfastados()) {
            throw erro("As quantidades precisam corresponder às pessoas e categorias informadas.");
        }
        if (pessoas.size() > 400) throw erro("Informe no máximo 400 pessoas por relatório.");
    }

    private Map<String, Object> validarDados(Map<String, Object> dadosOriginais) {
        if (dadosOriginais == null || dadosOriginais.isEmpty()) return new HashMap<>();
        if (dadosOriginais.size() > 50) throw erro("O relatório pode conter no máximo 50 campos dinâmicos.");
        Map<String, Object> dados = new HashMap<>();
        dadosOriginais.forEach((chave, valor) -> {
            if (chave == null || chave.isBlank() || chave.length() > 80 || valor == null
                    || valor instanceof Map<?, ?> || valor instanceof List<?>) {
                throw erro("Os campos dinâmicos precisam ter identificadores curtos e valores simples.");
            }
            if (valor instanceof String texto && texto.length() > 5000) throw erro("Um valor informado ultrapassa o limite permitido.");
            if (valor != null && !(valor instanceof String || valor instanceof Number || valor instanceof Boolean)) {
                throw erro("Tipo de valor inválido em um campo dinâmico.");
            }
            dados.put(chave, valor);
        });
        return dados;
    }

    private void validarCamposDaMissao(Missao missao, Map<String, Object> dadosRelatorio,
                                       List<PessoaRascunho> pessoas) {
        List<Map<String, Object>> campos = missao.getCamposRelatorio() == null
                ? List.of() : missao.getCamposRelatorio();
        Map<String, Map<String, Object>> camposRelatorio = new HashMap<>();
        Map<String, Map<String, Object>> camposPessoa = new HashMap<>();
        for (Map<String, Object> campo : campos) {
            String id = Objects.toString(campo.get("id"), "");
            if ("PESSOA".equalsIgnoreCase(Objects.toString(campo.get("escopo"), "RELATORIO")))
                camposPessoa.put(id, campo);
            else camposRelatorio.put(id, campo);
        }
        validarDadosDoEscopo(dadosRelatorio, camposRelatorio);
        for (PessoaRascunho pessoa : pessoas) {
            Map<String, Object> dadosPessoa = new HashMap<>(pessoa.dados());
            dadosPessoa.remove("categoria");
            validarDadosDoEscopo(dadosPessoa, camposPessoa);
        }
    }

    private void validarDadosDoEscopo(Map<String, Object> dados, Map<String, Map<String, Object>> campos) {
        if (!campos.keySet().containsAll(dados.keySet()))
            throw erro("O relatório contém campos que não foram definidos para esta missão.");
        for (Map.Entry<String, Map<String, Object>> entrada : campos.entrySet()) {
            String id = entrada.getKey();
            Map<String, Object> campo = entrada.getValue();
            Object valor = dados.get(id);
            String titulo = Objects.toString(campo.get("titulo"), id);
            boolean obrigatorio = Boolean.TRUE.equals(campo.get("obrigatorio"));
            if (valor == null) {
                if (obrigatorio) throw erro("Confira a resposta obrigatória: " + titulo + ".");
                continue;
            }
            if (!(valor instanceof String || valor instanceof Number))
                throw erro("Tipo de resposta inválido: " + titulo + ".");
            String texto = Objects.toString(valor);
            if (texto.length() > 5000 || (obrigatorio && texto.isBlank()))
                throw erro("Confira a resposta: " + titulo + ".");
            String tipo = Objects.toString(campo.get("tipo"), "");
            if ("numero".equals(tipo) && !texto.isBlank()) {
                java.math.BigDecimal numero;
                try { numero = new java.math.BigDecimal(texto); }
                catch (NumberFormatException erro) { throw erro("Número inválido para " + titulo + "."); }
                if (numero.signum() < 0 || numero.compareTo(java.math.BigDecimal.valueOf(1_000_000)) > 0)
                    throw erro("Informe um número entre 0 e 1.000.000 para " + titulo + ".");
            }
            if ("selecao".equals(tipo) && !texto.isBlank()) {
                Object opcoes = campo.get("opcoes");
                if (!(opcoes instanceof List<?> lista) || !lista.contains(texto))
                    throw erro("Opção inválida para " + titulo + ".");
            }
        }
    }

    private String nomeValido(String nome) {
        if (nome == null || nome.isBlank() || nome.length() > 200) throw erro("Cada pessoa precisa ter um nome válido.");
        return ESPACOS.matcher(nome.trim()).replaceAll(" ");
    }

    private static String normalizarNome(String nome) {
        return ESPACOS.matcher(nome.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    private static String categoria(String entrada) {
        if (entrada == null || entrada.isBlank()) throw erro("Cada pessoa precisa ter uma categoria.");
        String categoria = entrada.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (categoria.equals("INTEGRANTE") || categoria.equals("JOVEM_FIRME")) return "INTEGRANTE";
        if (categoria.equals("NOVO") || categoria.equals("AFASTADO") || categoria.equals("NOVO_AFASTADO"))
            return "NOVO_AFASTADO";
        if (categoria.length() > 80) throw erro("Categoria de pessoa muito longa.");
        return categoria;
    }

    private static boolean categoriaNovo(String categoria) {
        return "NOVO_AFASTADO".equals(categoria) || categoria.startsWith("NOVO_AFASTADO_")
                || categoria.startsWith("NOVO_") || categoria.startsWith("AFASTADO_");
    }

    private static Map<String, Object> aviso(String codigo, String mensagem) {
        return Map.of("codigo", codigo, "mensagem", mensagem);
    }

    private record PessoaRascunho(String nome, String nomeNormalizado, int ocorrencia,
                                  String categoria, Map<String, Object> dados) {}

    private static void exigirEnviado(Relatorio relatorio) {
        if (!"ENVIADO".equals(relatorio.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este relatório já foi revisado e não pode ser alterado.");
        }
    }

    private Relatorio buscar(UUID id) {
        return relatorioRepositorio.buscarComReferencias().stream()
                .filter(relatorio -> relatorio.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relatório não encontrado."));
    }

    private static ResponseStatusException erro(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
