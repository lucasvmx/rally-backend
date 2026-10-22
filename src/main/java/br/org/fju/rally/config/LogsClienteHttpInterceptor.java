package br.org.fju.rally.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Registra metadados sem expor corpos, credenciais ou URLs com parâmetros. */
public class LogsClienteHttpInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger(LogsClienteHttpInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long inicio = System.nanoTime();
        String chamadaId = UUID.randomUUID().toString();
        String requestId = MDC.get("request_id");
        String destino = request.getURI().getHost();
        log.info("http_saida_inicio request_id={} chamada_id={} metodo={} destino={}",
                requestId, chamadaId, request.getMethod(), destino);
        try {
            ClientHttpResponse resposta = execution.execute(request, body);
            return new ClientHttpResponse() {
                private final AtomicBoolean registrado = new AtomicBoolean();

                @Override
                public HttpStatusCode getStatusCode() throws IOException { return resposta.getStatusCode(); }

                @Override
                public String getStatusText() throws IOException { return resposta.getStatusText(); }

                @Override
                public HttpHeaders getHeaders() { return resposta.getHeaders(); }

                @Override
                public InputStream getBody() throws IOException { return resposta.getBody(); }

                @Override
                public void close() {
                    try {
                        resposta.close();
                    } finally {
                        if (registrado.compareAndSet(false, true)) {
                            String status;
                            try {
                                status = String.valueOf(resposta.getStatusCode().value());
                            } catch (IOException erro) {
                                status = "indisponivel";
                            }
                            log.info("http_saida_fim request_id={} chamada_id={} metodo={} destino={} status={} duracao_ms={}",
                                    requestId, chamadaId, request.getMethod(), destino, status,
                                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
                        }
                    }
                }
            };
        } catch (IOException | RuntimeException erro) {
            log.warn("http_saida_falha request_id={} chamada_id={} metodo={} destino={} erro={} duracao_ms={}",
                    requestId, chamadaId, request.getMethod(), destino, erro.getClass().getSimpleName(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
            throw erro;
        }
    }
}
