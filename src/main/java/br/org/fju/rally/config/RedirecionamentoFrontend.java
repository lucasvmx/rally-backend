package br.org.fju.rally.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Redireciona para telas do frontend usando a origem configurada, sem depender dos headers de proxy.
 * O proxy da Vercel nem sempre entrega o header Forwarded esperado, e um Location montado a partir
 * dele levava o navegador para o host da API. Sem origem configurada, o redirecionamento é relativo.
 */
@Component
public class RedirecionamentoFrontend {

    private final String origem;

    public RedirecionamentoFrontend(@Value("${rally.frontend.origem:}") String origem) {
        this.origem = validar(origem.trim());
    }

    public void redirecionar(HttpServletResponse resposta, String caminho) throws IOException {
        if (!caminho.startsWith("/") || caminho.startsWith("//")) {
            throw new IllegalArgumentException("O caminho de redirecionamento deve ser relativo à raiz do frontend.");
        }
        resposta.sendRedirect(origem + caminho);
    }

    private static String validar(String origem) {
        if (origem.isEmpty()) return origem;
        String semBarraFinal = origem.replaceAll("/+$", "");
        URI uri = URI.create(semBarraFinal);
        boolean esquemaValido = "https".equals(uri.getScheme()) || "http".equals(uri.getScheme());
        boolean somenteOrigem = uri.getHost() != null && uri.getRawPath().isEmpty()
                && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
        if (!esquemaValido || !somenteOrigem) {
            throw new IllegalStateException("rally.frontend.origem deve conter apenas esquema, host e porta, como https://rallydastribos.delcore.com.br.");
        }
        return semBarraFinal;
    }
}
