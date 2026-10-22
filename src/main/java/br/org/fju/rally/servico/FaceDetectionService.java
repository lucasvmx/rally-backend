package br.org.fju.rally.servico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

/** Cliente servidor-servidor do detector. A URL nunca vem dos dados enviados pelo usuário. */
@Service
public class FaceDetectionService {
    private static final Set<String> TIPOS_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int LIMITE_RESPOSTA = 80 * 1024 * 1024;

    private final RestClient cliente;

    public FaceDetectionService(RestClient.Builder construtor,
                                @Value("${rally.face-detection.endereco}") String endereco,
                                @Value("${rally.face-detection.timeout-ms:40000}") long timeoutMs) {
        URI destino = URI.create(endereco);
        if (!("http".equalsIgnoreCase(destino.getScheme()) || "https".equalsIgnoreCase(destino.getScheme()))
                || destino.getHost() == null || destino.getUserInfo() != null
                || destino.getQuery() != null || destino.getFragment() != null
                || !(destino.getPath().isEmpty() || "/".equals(destino.getPath()))) {
            throw new IllegalArgumentException("RALLY_FACE_DETECTION_URL precisa ser uma origem HTTP(S) válida.");
        }
        long prazoMs = Math.max(1_000, Math.min(timeoutMs, 45_000));
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
        fabrica.setReadTimeout(Duration.ofMillis(prazoMs));
        this.cliente = construtor.clone().baseUrl(endereco).requestFactory(fabrica).build();
    }

    public Resultado detectar(byte[] original, String tipoOriginal) {
        if (original == null || original.length == 0 || !TIPOS_PERMITIDOS.contains(tipoOriginal)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A foto precisa ser JPEG, PNG ou WebP.");
        }
        try {
            return cliente.post()
                    .uri("/v1/detect")
                    .contentType(MediaType.parseMediaType(tipoOriginal))
                    .body(original)
                    .exchange((requisicao, resposta) -> {
                        if (!resposta.getStatusCode().is2xxSuccessful()) {
                            HttpStatus status = resposta.getStatusCode().is4xxClientError()
                                    && resposta.getStatusCode().value() == 400
                                    ? HttpStatus.BAD_REQUEST
                                    : resposta.getStatusCode().value() == 413
                                        ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.SERVICE_UNAVAILABLE;
                            throw new ResponseStatusException(status, status == HttpStatus.BAD_REQUEST
                                    ? "A foto enviada não pôde ser processada."
                                    : status == HttpStatus.PAYLOAD_TOO_LARGE
                                        ? "A foto excede os limites de resolução do serviço de detecção."
                                        : "O serviço de detecção está indisponível. Tente novamente.");
                        }

                        MediaType tipoResposta = resposta.getHeaders().getContentType();
                        if (tipoResposta == null || !tipoResposta.isCompatibleWith(MediaType.parseMediaType(tipoOriginal))) {
                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "O serviço de detecção retornou uma imagem inválida.");
                        }
                        String tipoRecebido = resposta.getHeaders().getFirst("X-Original-Media-Type");
                        if (!tipoOriginal.equalsIgnoreCase(tipoRecebido)) {
                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "O serviço de detecção retornou uma resposta incompatível.");
                        }
                        long contagem = lerContagem(resposta.getHeaders().getFirst("X-Face-Count"));
                        byte[] anotada = lerLimitado(resposta.getBody(), LIMITE_RESPOSTA);
                        if (anotada.length == 0 || !assinaturaValida(anotada, tipoOriginal)) {
                            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "O serviço de detecção retornou uma imagem vazia.");
                        }
                        return new Resultado(anotada, contagem);
                    });
        } catch (ResponseStatusException excecao) {
            throw excecao;
        } catch (RestClientException | IOException excecao) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível processar a foto agora. Tente novamente.");
        }
    }

    private static long lerContagem(String valor) {
        if (valor == null || !valor.matches("[0-9]{1,9}")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "O serviço de detecção retornou uma contagem inválida.");
        }
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException excecao) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "O serviço de detecção retornou uma contagem inválida.");
        }
    }

    private static byte[] lerLimitado(InputStream entrada, int limite) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int lidos;
        while ((lidos = entrada.read(buffer)) != -1) {
            total += lidos;
            if (total > limite) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "A imagem anotada retornada excede o limite permitido.");
            }
            saida.write(buffer, 0, lidos);
        }
        return saida.toByteArray();
    }

    private static boolean assinaturaValida(byte[] bytes, String tipo) {
        if (bytes.length < 12) return false;
        return switch (tipo) {
            case "image/jpeg" -> bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
            case "image/png" -> bytes[0] == (byte) 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                    && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
            case "image/webp" -> new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }

    public record Resultado(byte[] imagemAnotada, long contagemRostos) {}
}
