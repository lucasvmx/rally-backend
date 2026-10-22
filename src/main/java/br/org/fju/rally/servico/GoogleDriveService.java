package br.org.fju.rally.servico;

import br.org.fju.rally.dominio.ArquivoDrive;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.repositorio.ArquivoDriveRepositorio;
import br.org.fju.rally.repositorio.RelatorioRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
public class GoogleDriveService {
    private static final String TIPO_PASTA = "application/vnd.google-apps.folder";

    private final RestClient.Builder restClientBuilder;
    private final ObjectProvider<OAuth2AuthorizedClientService> clientesAutorizados;
    private final RelatorioRepositorio relatorioRepositorio;
    private final ArquivoDriveRepositorio arquivoRepositorio;
    private final AcessoService acessoService;
    private final String pastaCentral;

    public GoogleDriveService(RestClient.Builder restClientBuilder,
                              ObjectProvider<OAuth2AuthorizedClientService> clientesAutorizados,
                              RelatorioRepositorio relatorioRepositorio,
                              ArquivoDriveRepositorio arquivoRepositorio,
                              AcessoService acessoService,
                              @Value("${rally.google-drive.pasta-central:}") String pastaCentral) {
        this.restClientBuilder = restClientBuilder;
        this.clientesAutorizados = clientesAutorizados;
        this.relatorioRepositorio = relatorioRepositorio;
        this.arquivoRepositorio = arquivoRepositorio;
        this.acessoService = acessoService;
        this.pastaCentral = pastaCentral;
    }

    public ArquivoDrive enviar(UUID relatorioId, MultipartFile arquivo,
                               HttpServletRequest requisicao, Authentication autenticacao) {
        Relatorio relatorio = relatorioRepositorio.buscarComReferencias().stream()
                .filter(item -> item.getId().equals(relatorioId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relatório não encontrado."));
        validarAcessoAoRelatorio(relatorio, requisicao);
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "O relatório não pode receber arquivos depois do envio. Inclua a foto no formulário do relatório.");
    }

    private void validarAcessoAoRelatorio(Relatorio relatorio, HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso acesso = acessoService.contexto(requisicao);
        if (!acesso.podeAcessarTribo(relatorio.getTribo().getId(), relatorio.getTribo().getNacao().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Você só pode anexar fotos da sua tribo ou nação.");
        }
    }

    private String tokenGoogle(Authentication autenticacao) {
        String registrationId;
        String principalName;
        if (autenticacao instanceof OAuth2AuthenticationToken oauth) {
            registrationId = oauth.getAuthorizedClientRegistrationId();
            principalName = oauth.getName();
        } else if (autenticacao instanceof JwtAuthenticationToken jwt
                && jwt.getToken().getClaimAsString("google_sub") != null) {
            registrationId = "google";
            principalName = jwt.getToken().getClaimAsString("google_sub");
        } else {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Conecte uma conta Google com acesso ao Drive antes de enviar fotos.");
        }
        OAuth2AuthorizedClientService servico = clientesAutorizados.getIfAvailable();
        if (servico == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A conexão com o Google Drive ainda não foi configurada.");
        }
        OAuth2AuthorizedClient cliente = servico.loadAuthorizedClient(registrationId, principalName);
        if (cliente == null || cliente.getAccessToken() == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "A sessão Google não possui autorização para o Drive.");
        }
        return cliente.getAccessToken().getTokenValue();
    }

    private String encontrarOuCriarPasta(String token, String nome, String pai) {
        RestClient cliente = clienteDrive(token);
        String consulta = "name = '" + nome.replace("'", "\\'") + "' and mimeType = '" + TIPO_PASTA
                + "' and trashed = false" + (pai == null || pai.isBlank() ? "" : " and '" + pai + "' in parents");
        JsonNode busca = cliente.get()
                .uri(uri -> uri.path("/files").queryParam("q", consulta).queryParam("fields", "files(id,name)").build())
                .retrieve().body(JsonNode.class);
        if (busca != null && busca.at("/files/0/id").isValueNode()) {
            return busca.at("/files/0/id").asText();
        }
        Map<String, Object> metadados = pai == null || pai.isBlank()
                ? Map.of("name", nome, "mimeType", TIPO_PASTA)
                : Map.of("name", nome, "mimeType", TIPO_PASTA, "parents", new String[]{pai});
        JsonNode criada = cliente.post().uri("/files").contentType(MediaType.APPLICATION_JSON)
                .body(metadados).retrieve().body(JsonNode.class);
        if (criada == null || !criada.at("/id").isValueNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Google Drive não retornou a pasta criada.");
        }
        return criada.at("/id").asText();
    }

    private String enviarArquivo(String token, MultipartFile arquivo, String pasta) throws IOException {
        RestClient cliente = clienteUpload(token);
        String nome = nomeSeguro(arquivo.getOriginalFilename() == null ? "foto" : arquivo.getOriginalFilename());
        Map<String, Object> metadados = Map.of("name", nome, "parents", new String[]{pasta});
        var resposta = cliente.post().uri(uri -> uri.path("/files").queryParam("uploadType", "resumable").build())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Upload-Content-Type", arquivo.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : arquivo.getContentType())
                .header("X-Upload-Content-Length", String.valueOf(arquivo.getSize()))
                .body(metadados).retrieve().toBodilessEntity();
        String localUpload = resposta.getHeaders().getFirst("Location");
        if (localUpload == null || localUpload.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Google Drive não iniciou o envio da foto.");
        }
        JsonNode enviada = restClientBuilder.clone().build().put().uri(URI.create(localUpload))
                .contentType(MediaType.parseMediaType(arquivo.getContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE : arquivo.getContentType()))
                .body(new ByteArrayResource(arquivo.getBytes()) {
                    @Override
                    public String getFilename() { return nome; }
                }).retrieve().body(JsonNode.class);
        if (enviada == null || !enviada.at("/id").isValueNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "O Google Drive não confirmou a foto.");
        }
        return enviada.at("/id").asText();
    }

    private RestClient clienteDrive(String token) {
        return restClientBuilder.baseUrl("https://www.googleapis.com/drive/v3")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    private RestClient clienteUpload(String token) {
        return restClientBuilder.baseUrl("https://www.googleapis.com/upload/drive/v3")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    private String hash(MultipartFile arquivo) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(arquivo.getBytes()));
        } catch (Exception excecao) {
            throw new IOException("Não foi possível calcular o identificador da foto.", excecao);
        }
    }

    private String nomeSeguro(String nome) {
        if (nome == null || nome.isBlank()) return "sem-nome";
        return nome.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
