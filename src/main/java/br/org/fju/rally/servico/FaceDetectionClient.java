package br.org.fju.rally.servico;

import br.org.fju.rally.facedetection.v1.DetectFacesRequest;
import br.org.fju.rally.facedetection.v1.FaceDetectionServiceGrpc;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Cliente autenticado para a análise síncrona de imagens enviadas pelo formulário público. */
@Service
public class FaceDetectionClient {
    private static final int MAX_IMAGEM_ORIGINAL_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGEM_ANOTADA_BYTES = 80 * 1024 * 1024;
    private static final Set<String> TIPOS_ACEITOS = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern TOKEN_BEARER = Pattern.compile("[A-Za-z0-9._~+/-]+=*");
    private static final Metadata.Key<String> CABECALHO_AUTORIZACAO = Metadata.Key.of(
            "authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final ManagedChannel canal;
    private final FaceDetectionServiceGrpc.FaceDetectionServiceBlockingStub stub;
    private final String token;
    private final long timeoutMs;

    public FaceDetectionClient(@Value("${rally.face-detection.host:localhost}") String host,
                               @Value("${rally.face-detection.port:50051}") int port,
                               @Value("${rally.face-detection.token:}") String token,
                               @Value("${rally.face-detection.timeout-ms:30000}") long timeoutMs) {
        if (host == null || host.isBlank() || port < 1 || port > 65535 || timeoutMs < 1) {
            throw new IllegalArgumentException("A configuração do serviço de detecção facial é inválida.");
        }
        this.token = token == null ? "" : token.trim();
        if (!this.token.isBlank() && !TOKEN_BEARER.matcher(this.token).matches()) {
            throw new IllegalArgumentException("O token do serviço de detecção facial tem formato inválido.");
        }
        this.timeoutMs = timeoutMs;
        this.canal = ManagedChannelBuilder.forAddress(host.trim(), port)
                .usePlaintext()
                .maxInboundMessageSize(96 * 1024 * 1024)
                .build();

        Metadata cabecalhos = new Metadata();
        // Token anexado por chamada para não expor segredo em endereço, payload ou logs.
        cabecalhos.put(CABECALHO_AUTORIZACAO, "Bearer " + this.token);
        this.stub = FaceDetectionServiceGrpc.newBlockingStub(canal)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabecalhos));
    }

    public Resultado detectar(byte[] imagem, String mediaTypeEsperado) {
        if (token.isBlank()) {
            throw indisponivel("A análise da foto não está configurada.");
        }

        try {
            var resposta = stub.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS).detectFaces(
                    DetectFacesRequest.newBuilder()
                            .setImageBytes(ByteString.copyFrom(imagem))
                            .build());
            String mediaType = resposta.getMediaType();
            ByteString originalBytes = resposta.getOriginalImageBytes();
            ByteString anotadaBytes = resposta.getAnnotatedImageBytes();
            if (!TIPOS_ACEITOS.contains(mediaType) || !mediaType.equals(mediaTypeEsperado)
                    || originalBytes.isEmpty() || anotadaBytes.isEmpty()
                    || originalBytes.size() != imagem.length
                    || originalBytes.size() > MAX_IMAGEM_ORIGINAL_BYTES
                    || anotadaBytes.size() > MAX_IMAGEM_ANOTADA_BYTES) {
                throw falhaGateway();
            }

            byte[] original = originalBytes.toByteArray();
            byte[] anotada = anotadaBytes.toByteArray();
            if (!Arrays.equals(original, imagem) || !assinaturaValida(original, mediaType)
                    || !assinaturaValida(anotada, mediaType)) {
                throw falhaGateway();
            }
            return new Resultado(original, anotada, mediaType,
                    Integer.toUnsignedLong(resposta.getFaceCount()));
        } catch (StatusRuntimeException excecao) {
            throw traduzirFalha(excecao.getStatus().getCode());
        }
    }

    private static ResponseStatusException traduzirFalha(Status.Code codigo) {
        if (codigo == Status.Code.INVALID_ARGUMENT) {
            return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A foto enviada não pôde ser processada.");
        }
        if (codigo == Status.Code.RESOURCE_EXHAUSTED || codigo == Status.Code.DEADLINE_EXCEEDED
                || codigo == Status.Code.UNAVAILABLE || codigo == Status.Code.UNAUTHENTICATED
                || codigo == Status.Code.PERMISSION_DENIED || codigo == Status.Code.CANCELLED) {
            return indisponivel("A análise da foto está temporariamente indisponível. Tente novamente.");
        }
        return falhaGateway();
    }

    private static ResponseStatusException indisponivel(String mensagem) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, mensagem);
    }

    private static ResponseStatusException falhaGateway() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "O serviço de análise retornou uma resposta inválida.");
    }

    private static boolean assinaturaValida(byte[] bytes, String mediaType) {
        if (bytes.length < 12) return false;
        return switch (mediaType) {
            case "image/jpeg" -> bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                    && bytes[2] == (byte) 0xff;
            case "image/png" -> Arrays.equals(Arrays.copyOf(bytes, 8),
                    new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            case "image/webp" -> new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
    }

    @PreDestroy
    public void fechar() {
        canal.shutdown();
        try {
            if (!canal.awaitTermination(5, TimeUnit.SECONDS)) canal.shutdownNow();
        } catch (InterruptedException excecao) {
            canal.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public record Resultado(byte[] imagemOriginal, byte[] imagemAnotada, String mediaType, long contagemRostos) {
        public Resultado {
            imagemOriginal = imagemOriginal.clone();
            imagemAnotada = imagemAnotada.clone();
        }

        @Override
        public byte[] imagemOriginal() {
            return imagemOriginal.clone();
        }

        @Override
        public byte[] imagemAnotada() {
            return imagemAnotada.clone();
        }
    }
}
