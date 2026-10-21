package br.org.fju.rally.api;

import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.RelatorioService;
import br.org.fju.rally.dominio.Tribo;
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
    public record Definicao(UUID missaoId, UUID rallyId, @NotBlank @Size(max=200) String titulo,
                           @Size(max=3000) String descricao, @NotNull @Size(max=30) List<@NotNull @Valid Campo> campos) {
        public Definicao(UUID missaoId, String titulo, String descricao, List<Campo> campos) {
            this(missaoId, null, titulo, descricao, campos);
        }
    }
    public record Formulario(UUID id, String token, boolean ativo, Definicao definicao) {}
    public record Envio(@NotNull @Valid RequisicaoRelatorio relatorio,
                        @NotNull UUID nacaoId,
                        @Size(max=5000) String nomesIntegrantes,
                        @Size(max=5000) String nomesNovosAfastados,
                        @NotBlank @Size(max=200) String preenchidoPor,
                        @NotNull @Size(max=30) Map<String, String> respostas) {}

    private Formulario mapearFormulario(java.sql.ResultSet rs, int linha) throws java.sql.SQLException {
        Definicao definicao = json.readValue(rs.getString("definicao"), Definicao.class);
        UUID rallyId = rs.getObject("rally_id", UUID.class);
        if (!Objects.equals(definicao.rallyId(), rallyId)) {
            definicao = new Definicao(definicao.missaoId(), rallyId, definicao.titulo(),
                    definicao.descricao(), definicao.campos());
        }
        return new Formulario(rs.getObject("id", UUID.class), rs.getString("token"),
                rs.getBoolean("ativo"), definicao);
    }

    @GetMapping("/formularios")
    public List<Formulario> listar(HttpServletRequest req) {
        acesso.exigirAdministracaoFormularios(req);
        return banco.query("SELECT * FROM formularios_publicos", this::mapearFormulario);
    }
    @Transactional
    @PostMapping("/formularios")
    public Formulario criar(@Valid @RequestBody Definicao definicao, HttpServletRequest req) {
        acesso.exigirAdministracaoFormularios(req);
        var missao = definicao.missaoId() == null ? null : missoes.findById(definicao.missaoId())
                .orElseThrow(() -> erro("Missão não encontrada."));
        UUID rallyId = definicao.rallyId();
        if (rallyId == null && missao != null && missao.getRally() != null) rallyId = missao.getRally().getId();
        if (rallyId == null && missao == null) throw erro("Selecione o rally deste formulário.");
        if (rallyId != null && banco.query("SELECT id FROM rallies WHERE id = ?",
                (rs, n) -> rs.getObject(1, UUID.class), rallyId).isEmpty()) throw erro("Rally não encontrado.");
        if (rallyId != null && missao != null && missao.getRally() != null
                && !rallyId.equals(missao.getRally().getId())) throw erro("A missão não pertence ao rally selecionado.");
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
        Definicao definicaoSalva = new Definicao(definicao.missaoId(), rallyId, definicao.titulo(),
                definicao.descricao(), definicao.campos());
        Formulario form = new Formulario(UUID.randomUUID(), HexFormat.of().formatHex(segredo), true, definicaoSalva);
        banco.update("INSERT INTO formularios_publicos(id, token, definicao, missao_id, rally_id) VALUES (?, ?, ?, ?, ?)",
            form.id(), form.token(), json.writeValueAsString(form.definicao()), definicao.missaoId(), rallyId);
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
            this::mapearFormulario, token);
        if (lista.isEmpty()) throw inexistente();
        return lista.getFirst();
    }
    @GetMapping("/publico/formularios/{token}")
    public Map<String, Object> publico(@PathVariable String token) {
        Formulario form = buscar(token);
        var missoesDoFormulario = missoes.buscarTodasComRally().stream()
                .filter(missao -> form.definicao().missaoId() == null || form.definicao().missaoId().equals(missao.getId()))
                .filter(missao -> form.definicao().rallyId() == null || missao.getRally() != null
                        && missao.getRally().getId().equals(form.definicao().rallyId()))
                .map(RespostaMissaoPublica::de)
                .toList();
        boolean exigeFoto = form.definicao().missaoId() == null || missoes.findById(form.definicao().missaoId())
                .map(br.org.fju.rally.dominio.Missao::isExigeFoto).orElse(true);
        var tribosAtivas = tribos.buscarTodasComNacao().stream().filter(Tribo::isAtiva).toList();
        Set<UUID> nacoesComTriboAtiva = tribosAtivas.stream().map(tribo -> tribo.getNacao().getId())
                .collect(java.util.stream.Collectors.toSet());
        return Map.of("definicao", form.definicao(),
            "nacoes", nacoes.findAll().stream().filter(nacao -> nacoesComTriboAtiva.contains(nacao.getId()))
                    .map(RespostaNacao::de).toList(),
            "tribos", tribosAtivas.stream().map(RespostaTribo::de).toList(),
            "missoes", missoesDoFormulario,
            "exige_foto", exigeFoto);
    }
    @PostMapping(value="/publico/formularios/{token}/respostas", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, Object> enviar(@PathVariable String token, @Valid @RequestPart("dados") Envio envio,
                                   @RequestPart(value="arquivo", required=false) List<MultipartFile> arquivos) throws IOException {
        return processarEnvio(token, envio, arquivos);
    }

    private Map<String, Object> processarEnvio(String token, Envio envio, List<MultipartFile> arquivos) throws IOException {
        Formulario form = buscar(token);
        if (form.definicao().missaoId() != null && !form.definicao().missaoId().equals(envio.relatorio().missaoId())) throw erro("Missão inválida.");
        var missao = missoes.findById(envio.relatorio().missaoId()).orElseThrow(() -> erro("Missão inválida."));
        var tribo = tribos.findById(envio.relatorio().triboId()).orElseThrow(() -> erro("Tribo inválida."));
        if (form.definicao().rallyId() != null && (missao.getRally() == null
                || !form.definicao().rallyId().equals(missao.getRally().getId()))) throw erro("Missão inválida para este link.");
        if (!tribo.isAtiva()) throw erro("A tribo selecionada está inativa.");
        if (!tribo.getNacao().getId().equals(envio.nacaoId())) throw erro("A tribo selecionada não pertence à nação informada.");
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
        List<Campo> camposDoFormulario = form.definicao().rallyId() == null
                ? form.definicao().campos() : camposDaMissao(missao);
        for (Campo campo : camposDoFormulario) {
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
        camposDoFormulario.forEach(campo -> camposRecebidos.put(
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
    private static List<Campo> camposDaMissao(br.org.fju.rally.dominio.Missao missao) {
        if (missao.getCamposRelatorio() == null) return List.of();
        return missao.getCamposRelatorio().stream().map(campo -> {
            Object opcoes = campo.get("opcoes");
            List<String> opcoesTexto = opcoes instanceof List<?> lista
                    ? lista.stream().map(Objects::toString).toList() : List.of();
            return new Campo(Objects.toString(campo.get("id"), ""),
                    Objects.toString(campo.get("titulo"), ""), Objects.toString(campo.get("tipo"), ""),
                    Boolean.TRUE.equals(campo.get("obrigatorio")), opcoesTexto,
                    Objects.toString(campo.getOrDefault("escopo", "RELATORIO"), "RELATORIO")
                            .toUpperCase(Locale.ROOT));
        }).toList();
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
