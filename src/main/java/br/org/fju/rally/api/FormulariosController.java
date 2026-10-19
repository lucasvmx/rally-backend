package br.org.fju.rally.api;

import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.RelatorioService;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.NacaoRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.security.SecureRandom;
import java.util.*;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1")
public class FormulariosController {
    private final JdbcTemplate banco;
    private final JsonMapper json;
    private final AcessoService acesso;
    private final RelatorioService relatorios;
    private final MissaoRepositorio missoes;
    private final NacaoRepositorio nacoes;
    private final TriboRepositorio tribos;
    public FormulariosController(JdbcTemplate banco, JsonMapper json, AcessoService acesso,
            RelatorioService relatorios, MissaoRepositorio missoes, NacaoRepositorio nacoes, TriboRepositorio tribos) {
        this.banco = banco; this.json = json; this.acesso = acesso;
        this.relatorios = relatorios; this.missoes = missoes; this.nacoes = nacoes; this.tribos = tribos;
    }
    public record Campo(@NotBlank @Size(max=80) String id, @NotBlank @Size(max=200) String titulo,
                        @NotNull @Pattern(regexp="texto|paragrafo|numero|selecao") String tipo,
                        boolean obrigatorio, @Size(max=50) List<@NotBlank @Size(max=200) String> opcoes,
                        @Pattern(regexp="RELATORIO|PESSOA") String escopo) {
        public Campo(String id, String titulo, String tipo, boolean obrigatorio, List<String> opcoes) {
            this(id, titulo, tipo, obrigatorio, opcoes, "RELATORIO");
        }
        public Campo {
            if (escopo == null || escopo.isBlank()) escopo = "RELATORIO";
        }
    }
    public record Definicao(UUID missaoId, @NotBlank @Size(max=200) String titulo,
                           @Size(max=3000) String descricao, @NotNull @Size(max=30) List<@NotNull @Valid Campo> campos) {}
    public record Formulario(UUID id, String token, boolean ativo, Definicao definicao) {}
    public record Envio(@NotNull @Valid RequisicaoRelatorio relatorio,
                        @NotNull UUID nacaoId,
                        @Size(max=5000) String nomesIntegrantes,
                        @Size(max=5000) String nomesNovosAfastados,
                        @NotBlank @Size(max=200) String preenchidoPor,
                        @NotNull @Size(max=30) Map<String, String> respostas) {}

    @GetMapping("/formularios")
    public List<Formulario> listar(HttpServletRequest req) {
        acesso.exigirAdministracaoFormularios(req);
        return banco.query("SELECT * FROM formularios_publicos", (rs, n) -> new Formulario(
            rs.getObject("id", UUID.class), rs.getString("token"), rs.getBoolean("ativo"),
            json.readValue(rs.getString("definicao"), Definicao.class)));
    }
    @PostMapping("/formularios")
    public Formulario criar(@Valid @RequestBody Definicao definicao, HttpServletRequest req) {
        acesso.exigirAdministracaoFormularios(req);
        var missao = definicao.missaoId() == null ? null : missoes.findById(definicao.missaoId())
                .orElseThrow(() -> erro("Missão não encontrada."));
        Set<String> ids = new HashSet<>();
        Set<String> camposReservados = Set.of("nomes_integrantes", "nomes_novos_afastados", "preenchido_por");
        for (Campo campo : definicao.campos()) {
            if (campo.tipo() == null || !ids.add(campo.id()) || camposReservados.contains(campo.id())
                    || !Set.of("RELATORIO", "PESSOA").contains(campo.escopo())) throw erro("Pergunta inválida ou repetida.");
            if (campo.tipo().equals("selecao") && (campo.opcoes() == null || campo.opcoes().isEmpty()))
                throw erro("Informe as opções da pergunta.");
        }
        if (missao != null) {
            Map<String, String> tiposEsperados = new HashMap<>();
            for (Map<String, Object> campo : missao.getCamposRelatorio()) {
                tiposEsperados.put(Objects.toString(campo.get("id")), Objects.toString(campo.get("tipo"))
                        + ":" + Objects.toString(campo.getOrDefault("escopo", "RELATORIO"), "RELATORIO")
                        .toUpperCase(Locale.ROOT));
            }
            Map<String, String> tiposRecebidos = new HashMap<>();
            definicao.campos().forEach(campo -> tiposRecebidos.put(campo.id(), campo.tipo() + ":" + campo.escopo()));
            if (!tiposEsperados.equals(tiposRecebidos))
                throw erro("Os campos do formulário precisam corresponder aos campos publicados na missão.");
        }
        byte[] segredo = new byte[32]; new SecureRandom().nextBytes(segredo);
        Formulario form = new Formulario(UUID.randomUUID(), HexFormat.of().formatHex(segredo), true, definicao);
        banco.update("INSERT INTO formularios_publicos(id, token, definicao) VALUES (?, ?, ?)",
            form.id(), form.token(), json.writeValueAsString(form.definicao()));
        return form;
    }
    @PostMapping("/formularios/{id}/desativar")
    public void desativar(@PathVariable UUID id, HttpServletRequest req) {
        acesso.exigirAdministracaoFormularios(req);
        banco.update("UPDATE formularios_publicos SET ativo = FALSE WHERE id = ?", id);
    }
    private Formulario buscar(String token) {
        if (!token.matches("[a-f0-9]{64}")) throw inexistente();
        List<Formulario> lista = banco.query("SELECT * FROM formularios_publicos WHERE token = ? AND ativo = TRUE",
            (rs, n) -> new Formulario(rs.getObject("id", UUID.class), token, true,
                json.readValue(rs.getString("definicao"), Definicao.class)), token);
        if (lista.isEmpty()) throw inexistente();
        return lista.getFirst();
    }
    @GetMapping("/publico/formularios/{token}")
    public Map<String, Object> publico(@PathVariable String token) {
        Formulario form = buscar(token);
        var missoesDoFormulario = form.definicao().missaoId() == null
                ? missoes.buscarTodasComRally().stream().map(RespostaMissao::de).toList()
                : missoes.buscarPorIdComRally(form.definicao().missaoId()).stream().map(RespostaMissao::de).toList();
        boolean exigeFoto = form.definicao().missaoId() == null || missoes.findById(form.definicao().missaoId())
                .map(br.org.fju.rally.dominio.Missao::isExigeFoto).orElse(true);
        return Map.of("definicao", form.definicao(),
            "nacoes", nacoes.findAll().stream().map(RespostaNacao::de).toList(),
            "tribos", tribos.buscarTodasComNacao().stream().filter(Tribo::isAtiva).map(RespostaTribo::de).toList(),
            "missoes", missoesDoFormulario,
            "exige_foto", exigeFoto);
    }
    @PostMapping(value="/publico/formularios/{token}/respostas", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, Object> enviar(@PathVariable String token, @Valid @RequestPart("dados") Envio envio,
                                   @RequestPart(value="arquivo", required=false) List<MultipartFile> arquivos,
                                   HttpServletRequest requisicao) throws IOException {
        var coordenador = acesso.exigirCoordenador(requisicao);
        return enviarAutorizado(token, envio, arquivos, coordenador);
    }

    // O handler REST sempre valida o contexto de coordenador antes de chamar o processamento interno.
    Map<String, Object> enviar(String token, Envio envio, List<MultipartFile> arquivos) throws IOException {
        return enviarAutorizado(token, envio, arquivos, null);
    }

    private Map<String, Object> enviarAutorizado(String token, Envio envio, List<MultipartFile> arquivos,
                                                AcessoService.ContextoAcesso coordenador) throws IOException {
        Formulario form = buscar(token);
        if (form.definicao().missaoId() != null && !form.definicao().missaoId().equals(envio.relatorio().missaoId())) throw erro("Missão inválida.");
        var missao = missoes.findById(envio.relatorio().missaoId()).orElseThrow(() -> erro("Missão inválida."));
        var tribo = tribos.findById(envio.relatorio().triboId()).orElseThrow(() -> erro("Tribo inválida."));
        if (!tribo.getNacao().getId().equals(envio.nacaoId())) throw erro("A tribo selecionada não pertence à nação informada.");
        if (coordenador != null && !coordenador.podeAcessarTribo(tribo.getId(), tribo.getNacao().getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "O coordenador só pode enviar relatório para sua tribo ou nação.");
        List<PessoaRelatorio> pessoas = envio.relatorio().pessoas() == null || envio.relatorio().pessoas().isEmpty()
                ? null : envio.relatorio().pessoas();
        if (pessoas == null) {
            if (envio.nomesIntegrantes() == null || envio.nomesNovosAfastados() == null
                    || quantidadeNomes(envio.nomesIntegrantes()) != envio.relatorio().quantidadePessoas()
                    || quantidadeNomes(envio.nomesNovosAfastados()) != envio.relatorio().quantidadeNovosAfastados())
                throw erro("As quantidades do relatório não correspondem aos nomes informados.");
            pessoas = new ArrayList<>();
            adicionarPessoas(pessoas, envio.nomesIntegrantes(), "INTEGRANTE");
            adicionarPessoas(pessoas, envio.nomesNovosAfastados(), "NOVO_AFASTADO");
        }
        if (envio.respostas().values().stream().anyMatch(Objects::isNull))
            throw erro("Uma resposta não pode ser nula.");
        Set<String> permitidosRelatorio = new HashSet<>();
        Set<String> permitidosPessoa = new HashSet<>();
        Map<String, Campo> camposFormulario = new HashMap<>();
        for (Campo campo : form.definicao().campos()) {
            camposFormulario.put(campo.id(), campo);
            ("PESSOA".equals(campo.escopo()) ? permitidosPessoa : permitidosRelatorio).add(campo.id());
            if ("RELATORIO".equals(campo.escopo())) validarValor(campo, envio.respostas().get(campo.id()));
        }
        Map<String, String> camposEsperados = new HashMap<>();
        missao.getCamposRelatorio().forEach(campo -> camposEsperados.put(
                Objects.toString(campo.get("id")), Objects.toString(campo.get("tipo")) + ":"
                        + Objects.toString(campo.getOrDefault("escopo", "RELATORIO"), "RELATORIO")
                        .toUpperCase(Locale.ROOT)));
        Map<String, String> camposRecebidos = new HashMap<>();
        form.definicao().campos().forEach(campo -> camposRecebidos.put(
                campo.id(), campo.tipo() + ":" + campo.escopo()));
        if (!camposEsperados.equals(camposRecebidos)) throw erro("O formulário não corresponde aos campos desta missão.");
        if (!permitidosRelatorio.containsAll(envio.respostas().keySet())) throw erro("Pergunta desconhecida ou fora do escopo do relatório.");
        for (PessoaRelatorio pessoa : pessoas) {
            Map<String, Object> dadosPessoa = pessoa.dados() == null ? Map.of() : pessoa.dados();
            if (dadosPessoa.values().stream().anyMatch(Objects::isNull))
                throw erro("Um campo de pessoa não pode ser nulo.");
            if (!permitidosPessoa.containsAll(dadosPessoa.keySet())) throw erro("Campo de pessoa desconhecido.");
            for (String idCampo : permitidosPessoa) validarValor(camposFormulario.get(idCampo), dadosPessoa.get(idCampo));
        }
        boolean arquivoEnviado = arquivos != null && !arquivos.isEmpty()
                && arquivos.stream().anyMatch(arquivo -> arquivo != null && !arquivo.isEmpty());
        if (missao.isExigeFoto() && (!arquivoEnviado || arquivos.size() != 1))
            throw erro("Esta missão exige exatamente uma foto no envio do relatório.");
        if (arquivoEnviado && arquivos.size() != 1) throw erro("Envie somente uma foto por relatório.");
        MultipartFile arquivo = arquivoEnviado ? arquivos.getFirst() : null;
        byte[] conteudo = null;
        if (arquivo != null) {
            if (arquivo.getSize() > 5 * 1024 * 1024 || !Set.of("image/jpeg", "image/png", "image/webp").contains(Objects.toString(arquivo.getContentType(), "")))
                throw erro("Envie uma foto JPEG, PNG ou WebP de até 5 MB.");
            conteudo = arquivo.getBytes();
            if (!assinaturaValida(conteudo, arquivo.getContentType())) throw erro("O arquivo não corresponde ao formato de foto informado.");
        }
        Map<String, Object> dadosRelatorio = new LinkedHashMap<>(envio.respostas());
        RequisicaoRelatorio dadosDoRelatorio = new RequisicaoRelatorio(envio.relatorio().missaoId(),
                envio.relatorio().triboId(), envio.relatorio().quantidadePessoas(),
                envio.relatorio().quantidadeNovosAfastados(), envio.relatorio().observacoes(), pessoas,
                dadosRelatorio);
        RespostaRelatorio relatorio = relatorios.criarPublico(dadosDoRelatorio, arquivo != null);
        Map<String, Object> respostas = new LinkedHashMap<>(envio.respostas());
        respostas.put("nomes_integrantes", nomesPorCategoria(pessoas, false));
        respostas.put("nomes_novos_afastados", nomesPorCategoria(pessoas, true));
        respostas.put("pessoas", pessoas);
        respostas.put("preenchido_por", envio.preenchidoPor());
        banco.update("INSERT INTO respostas_formularios(relatorio_id, formulario_id, respostas, nome_arquivo, tipo_arquivo, arquivo) VALUES (?, ?, ?, ?, ?, ?)",
            relatorio.id(), form.id(), json.writeValueAsString(respostas), arquivo == null ? null : "foto",
                arquivo == null ? null : arquivo.getContentType(), conteudo);
        return Map.of("id", relatorio.id(), "avisos", relatorio.avisos());
    }
    @GetMapping("/formularios/respostas/{id}")
    public List<Map<String, Object>> respostas(@PathVariable UUID id, HttpServletRequest req) {
        acesso.exigirAdministracaoFormularios(req);
        return banco.query("SELECT r.respostas, r.nome_arquivo, f.definicao FROM respostas_formularios r JOIN formularios_publicos f ON f.id = r.formulario_id WHERE r.relatorio_id = ?",
            (rs, n) -> Map.of("respostas", json.readTree(rs.getString("respostas")), "definicao", json.readTree(rs.getString("definicao")), "tem_foto", rs.getString("nome_arquivo") != null), id);
    }
    @GetMapping("/formularios/respostas/{id}/foto")
    public ResponseEntity<byte[]> foto(@PathVariable UUID id, HttpServletRequest req) {
        acesso.exigirAdministracaoFormularios(req);
        return banco.query("SELECT arquivo, tipo_arquivo FROM respostas_formularios WHERE relatorio_id = ? AND arquivo IS NOT NULL",
            (rs, n) -> ResponseEntity.ok().header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(rs.getString("tipo_arquivo"))).body(rs.getBytes("arquivo")), id)
            .stream().findFirst().orElseThrow(FormulariosController::inexistente);
    }
    private static boolean assinaturaValida(byte[] bytes, String tipo) {
        if (bytes.length < 12) return false;
        return switch (tipo) {
            case "image/jpeg" -> bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
            case "image/png" -> Arrays.equals(Arrays.copyOf(bytes, 8), new byte[] {(byte)137, 80, 78, 71, 13, 10, 26, 10});
            case "image/webp" -> new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }
    private static int quantidadeNomes(String nomes) {
        int quantidade = (int) Arrays.stream(nomes.split("\\R"))
            .map(String::trim)
            .filter(nome -> !nome.isBlank())
            .filter(nome -> !nome.equalsIgnoreCase("nenhum"))
            .count();
        if (quantidade > 200) throw erro("Informe no máximo 200 nomes em cada lista.");
        return quantidade;
    }
    private static void adicionarPessoas(List<PessoaRelatorio> pessoas, String nomes, String categoria) {
        Arrays.stream(Objects.toString(nomes, "").split("\\R"))
                .map(String::trim)
                .filter(nome -> !nome.isBlank())
                .filter(nome -> !nome.equalsIgnoreCase("nenhum"))
                .forEach(nome -> pessoas.add(new PessoaRelatorio(nome, categoria, Map.of())));
    }
    private static void validarValor(Campo campo, Object valor) {
        if (valor == null) {
            if (campo.obrigatorio()) throw erro("Confira a resposta: " + campo.titulo());
            return;
        }
        if (!(valor instanceof String || valor instanceof Number || valor instanceof Boolean))
            throw erro("Tipo de resposta inválido: " + campo.titulo());
        String texto = Objects.toString(valor);
        if (texto.length() > 5000 || (campo.obrigatorio() && texto.isBlank()))
            throw erro("Confira a resposta: " + campo.titulo());
        if (!texto.isBlank() && campo.tipo().equals("numero")) {
            try { new java.math.BigDecimal(texto); } catch (NumberFormatException e) { throw erro("Número inválido."); }
        }
        if (!texto.isBlank() && campo.tipo().equals("selecao")
                && (campo.opcoes() == null || !campo.opcoes().contains(texto))) throw erro("Opção inválida.");
    }
    private static String nomesPorCategoria(List<PessoaRelatorio> pessoas, boolean novos) {
        return pessoas.stream().filter(pessoa -> categoriaNovo(pessoa.categoria()) == novos)
                .map(PessoaRelatorio::nome).map(Objects::toString).collect(java.util.stream.Collectors.joining("\n"));
    }
    private static boolean categoriaNovo(String categoria) {
        if (categoria == null) return false;
        String valor = categoria.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return List.of("NOVO", "AFASTADO", "NOVO_AFASTADO").contains(valor)
                || valor.startsWith("NOVO_") || valor.startsWith("AFASTADO_");
    }
    private static ResponseStatusException erro(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
    private static ResponseStatusException inexistente() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Link inválido ou desativado."); }
}
