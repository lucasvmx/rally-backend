package br.org.fju.rally.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Registra metadados, sem expor parâmetros, credenciais ou tokens nos caminhos. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LogsRequisicoesFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(LogsRequisicoesFilter.class);
    private final LongSupplier relogio;

    public LogsRequisicoesFilter() {
        this(System::nanoTime);
    }

    LogsRequisicoesFilter(LongSupplier relogio) {
        this.relogio = relogio;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String idAnterior = MDC.get("request_id");
        String id = UUID.randomUUID().toString();
        long inicio = relogio.getAsLong();
        Throwable falha = null;
        MDC.put("request_id", id);
        try {
            log.info("http_recebido_inicio request_id={} metodo={}", id, request.getMethod());
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException | Error erro) {
            falha = erro;
            throw erro;
        } finally {
            try {
                long duracao = TimeUnit.NANOSECONDS.toMillis(relogio.getAsLong() - inicio);
                Object rota = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                // Rotas desconhecidas podem conter tokens ou dados pessoais; nunca usar a URI bruta.
                String caminho = rota == null ? "[nao_mapeada]" : rota.toString();
                int status = falha != null && !response.isCommitted() ? 500 : response.getStatus();
                if (falha == null) {
                    log.info("http_recebido_fim request_id={} metodo={} rota={} status={} duracao_ms={}",
                            id, request.getMethod(), caminho, status, duracao);
                } else {
                    // A mensagem da exceção também pode conter dados da requisição.
                    log.warn("http_recebido_fim request_id={} metodo={} rota={} status={} duracao_ms={} erro={}",
                            id, request.getMethod(), caminho, status, duracao, falha.getClass().getSimpleName());
                }
            } finally {
                if (idAnterior == null) {
                    MDC.remove("request_id");
                } else {
                    MDC.put("request_id", idAnterior);
                }
            }
        }
    }
}
