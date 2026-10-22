package br.org.fju.rally.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

class LogsRequisicoesFilterTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(LogsRequisicoesFilter.class);
    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();
    private final AtomicLong nanos = new AtomicLong();
    private final LogsRequisicoesFilter filtro = new LogsRequisicoesFilter(nanos::get);

    @BeforeEach
    void iniciar() {
        eventos.start();
        logger.addAppender(eventos);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(eventos);
        eventos.stop();
        MDC.clear();
    }

    @Test
    void registraInicioFimStatusDuracaoERotaSemDadosSensiveis() throws Exception {
        var requisicao = new MockHttpServletRequest("POST", "/api/v1/publico/formularios/token-secreto/respostas");
        requisicao.setQueryString("senha=senha-secreta");
        requisicao.addHeader("Authorization", "Bearer credencial-secreta");
        requisicao.setContent("conteudo-secreto".getBytes());
        var resposta = new MockHttpServletResponse();
        filtro.doFilter(requisicao, resposta, (req, res) -> {
            assertThat(MDC.get("request_id")).isNotBlank();
            req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                    "/api/v1/publico/formularios/{token}/respostas");
            nanos.set(123_456_789);
            ((MockHttpServletResponse) res).setStatus(201);
        });

        assertThat(eventos.list).hasSize(2);
        String inicio = eventos.list.getFirst().getFormattedMessage();
        String fim = eventos.list.getLast().getFormattedMessage();
        assertThat(inicio).contains("http_recebido_inicio", "metodo=POST");
        assertThat(fim).contains("http_recebido_fim", "status=201", "duracao_ms=123",
                "rota=/api/v1/publico/formularios/{token}/respostas");
        String id = inicio.split("request_id=")[1].split(" ")[0];
        assertThat(fim).contains("request_id=" + id);
        assertThat(inicio + fim).doesNotContain("token-secreto", "senha-secreta", "credencial-secreta", "conteudo-secreto");
        assertThat(MDC.get("request_id")).isNull();
    }

    @Test
    void preservaFalhaOriginalELimpaContextoMesmoSemRotaMapeada() {
        var requisicao = new MockHttpServletRequest("GET", "/caminho/token-secreto");
        var resposta = new MockHttpServletResponse();
        var erro = new ServletException("conteudo-secreto");
        assertThatThrownBy(() -> filtro.doFilter(requisicao, resposta, (req, res) -> {
            nanos.set(7_000_000);
            throw erro;
        })).isSameAs(erro);

        String fim = eventos.list.getLast().getFormattedMessage();
        assertThat(fim).contains("status=500", "duracao_ms=7", "rota=[nao_mapeada]", "erro=ServletException")
                .doesNotContain("token-secreto", "conteudo-secreto");
        assertThat(MDC.get("request_id")).isNull();
    }

    @Test
    void capturaRespostaRejeitadaERestauraContextoAnterior() throws Exception {
        MDC.put("request_id", "contexto-anterior");
        MDC.put("outra_chave", "preservada");
        filtro.doFilter(new MockHttpServletRequest("GET", "/rota-privada"),
                new MockHttpServletResponse(), (req, res) -> ((MockHttpServletResponse) res).setStatus(401));

        assertThat(eventos.list.getLast().getFormattedMessage()).contains("status=401", "rota=[nao_mapeada]");
        assertThat(MDC.get("request_id")).isEqualTo("contexto-anterior");
        assertThat(MDC.get("outra_chave")).isEqualTo("preservada");
    }
}
