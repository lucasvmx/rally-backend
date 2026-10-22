package br.org.fju.rally.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LogsClienteHttpInterceptorTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(LogsClienteHttpInterceptor.class);
    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();

    @BeforeEach
    void preparar() {
        eventos.start();
        logger.addAppender(eventos);
        MDC.put("request_id", "entrada-123");
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(eventos);
        MDC.clear();
    }

    @Test
    void registraConclusaoDepoisDoConsumoDaRespostaSemExporSegredos() {
        var builder = new ClienteHttpConfig().construtorRestClient();
        var servidor = MockRestServiceServer.bindTo(builder).build();
        String url = "https://openrouter.ai/api/v1/chat/completions?chave=segredo-query";
        servidor.expect(requestTo(url)).andRespond(withSuccess("segredo-resposta", org.springframework.http.MediaType.TEXT_PLAIN));

        assertThat(builder.build().post().uri(url).header("Authorization", "Bearer segredo-token")
                .body("segredo-corpo").retrieve().body(String.class)).isEqualTo("segredo-resposta");

        servidor.verify();
        assertThat(eventos.list).hasSize(2);
        String fim = eventos.list.getLast().getFormattedMessage();
        assertThat(fim).contains("http_saida_fim", "request_id=entrada-123", "destino=openrouter.ai", "status=200")
                .matches(".*duracao_ms=\\d+$");
        assertThat(eventos.list.toString()).doesNotContain("segredo", "?chave", "/chat/completions");
    }

    @Test
    void registraFalhaDeTransporteSemMensagemDaExcecao() {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("https://openrouter.ai"));
        assertThatThrownBy(() -> new LogsClienteHttpInterceptor().intercept(request, new byte[0],
                (req, body) -> { throw new IOException("segredo-url"); })).isInstanceOf(IOException.class);
        assertThat(eventos.list).hasSize(2);
        assertThat(eventos.list.getLast().getFormattedMessage())
                .contains("http_saida_falha", "erro=IOException", "duracao_ms=").doesNotContain("segredo-url");
    }

    @Test
    void registraStatusDeErroEFechaRespostaUmaVezNoLog() throws IOException {
        var request = new MockClientHttpRequest(HttpMethod.GET, URI.create("https://openrouter.ai"));
        var resposta = new LogsClienteHttpInterceptor().intercept(request, new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.TOO_MANY_REQUESTS));
        assertThat(eventos.list).hasSize(1);
        resposta.close();
        resposta.close();
        assertThat(eventos.list).hasSize(2);
        assertThat(eventos.list.getLast().getFormattedMessage()).contains("status=429");
    }
}
