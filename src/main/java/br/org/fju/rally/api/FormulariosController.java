package br.org.fju.rally.api;

import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.RelatorioService;
import br.org.fju.rally.repositorio.MissaoRepositorio;
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
    private final TriboRepositorio tribos;
    public FormulariosController(JdbcTemplate banco, JsonMapper json, AcessoService acesso,
            RelatorioService relatorios, MissaoRepositorio missoes, TriboRepositorio tribos) {
        this.banco = banco; this.json = json; this.acesso = acesso;
        this.relatorios = relatorios; this.missoes = missoes; this.tribos = tribos;
    }
    public record Campo(@NotBlank @Size(max=80) String id, @NotBlank @Size(max=200) String titulo,
                        @NotNull @Pattern(regexp="texto|paragrafo|numero|selecao") String tipo,
                        boolean obrigatorio, @Size(max=50) List<@NotBlank @Size(max=200) String> opcoes) {}
    public record Definicao(@NotNull UUID missaoId, @NotBlank @Size(max=200) String titulo,
                           @Size(max=3000) String descricao, @NotNull @Size(max=30) List<@NotNull @Valid Campo> campos) {}
    public record Formulario(UUID id, String token, boolean ativo, Definicao definicao) {}
    public record Envio(@NotNull @Valid RequisicaoRelatorio relatorio,
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
        if (!missoes.existsById(definicao.missaoId())) throw erro("Missão inválida.");
        Set<String> ids = new HashSet<>();
        for (Campo campo : definicao.campos()) {
            if (campo.tipo() == null || !ids.add(campo.id())) throw erro("Pergunta inválida ou repetida.");
            if (campo.tipo().equals("selecao") && (campo.opcoes() == null || campo.opcoes().isEmpty()))
                throw erro("Informe as opções da pergunta.");
        }
        byte[] segredo = new byte[32]; new SecureRandom().nextBytes(segredo);
        Formulario form = new Formulario(UUID.randomUUID(), HexFormat.of().formatHex(segredo), true, definicao);
        banco.update("INSERT INTO formularios_publicos(id, token, definicao) VALUES (?, ?, ?)",
            form.id(), form.token(), json.writeValueAsString(definicao));
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
        return Map.of("definicao", form.definicao(), "tribos", tribos.findAll().stream().map(RespostaTribo::de).toList(),
            "exige_foto", missoes.findById(form.definicao().missaoId()).orElseThrow().isExigeFoto());
    }
    @PostMapping(value="/publico/formularios/{token}/respostas", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public Map<String, UUID> enviar(@PathVariable String token, @Valid @RequestPart("dados") Envio envio,
                                   @RequestPart(value="arquivo", required=false) MultipartFile arquivo) throws IOException {
        Formulario form = buscar(token);
        if (!form.definicao().missaoId().equals(envio.relatorio().missaoId())) throw erro("Missão inválida.");
        Set<String> permitidos = new HashSet<>();
        for (Campo campo : form.definicao().campos()) {
            permitidos.add(campo.id());
            String valor = envio.respostas().getOrDefault(campo.id(), "");
            if (valor == null || valor.length() > 5000 || (campo.obrigatorio() && valor.isBlank()))
                throw erro("Confira a resposta: " + campo.titulo());
            if (!valor.isBlank() && campo.tipo().equals("numero")) {
                try { new java.math.BigDecimal(valor); } catch (NumberFormatException e) { throw erro("Número inválido."); }
            }
            if (!valor.isBlank() && campo.tipo().equals("selecao") && !campo.opcoes().contains(valor)) throw erro("Opção inválida.");
        }
        if (!permitidos.containsAll(envio.respostas().keySet())) throw erro("Pergunta desconhecida.");
        boolean temFoto = arquivo != null && !arquivo.isEmpty();
        if (missoes.findById(form.definicao().missaoId()).orElseThrow().isExigeFoto() && !temFoto)
            throw erro("Esta missão exige uma foto.");
        if (temFoto && (arquivo.getSize() > 5 * 1024 * 1024 || !Set.of("image/jpeg", "image/png", "image/webp").contains(Objects.toString(arquivo.getContentType(), ""))))
            throw erro("Envie uma foto JPEG, PNG ou WebP de até 5 MB.");
        if (envio.relatorio().observacoes() != null && envio.relatorio().observacoes().length() > 5000) throw erro("Observações muito longas.");
        byte[] conteudo = temFoto ? arquivo.getBytes() : null;
        if (temFoto && !assinaturaValida(conteudo, arquivo.getContentType())) throw erro("O arquivo não corresponde ao formato de foto informado.");
        String nomeArquivo = temFoto ? Objects.toString(arquivo.getOriginalFilename(), "foto") : null;
        if (nomeArquivo != null && nomeArquivo.length() > 255) nomeArquivo = nomeArquivo.substring(nomeArquivo.length() - 255);
        RespostaRelatorio relatorio = relatorios.criarPublico(envio.relatorio());
        banco.update("INSERT INTO respostas_formularios(relatorio_id, formulario_id, respostas, nome_arquivo, tipo_arquivo, arquivo) VALUES (?, ?, ?, ?, ?, ?)",
            relatorio.id(), form.id(), json.writeValueAsString(envio.respostas()), nomeArquivo,
            temFoto ? arquivo.getContentType() : null, conteudo);
        return Map.of("id", relatorio.id());
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
    private static ResponseStatusException erro(String mensagem) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
    private static ResponseStatusException inexistente() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Link inválido ou desativado."); }
}
