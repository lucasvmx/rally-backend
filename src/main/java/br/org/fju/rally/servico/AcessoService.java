package br.org.fju.rally.servico;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AcessoService {
    @Value("${rally.autenticacao.modo-demonstracao:true}")
    private boolean modoDemonstracao = true;
    @Value("${rally.autenticacao.assistentes:}")
    private String assistentes = "";

    public void exigirAdministracaoFormularios(HttpServletRequest requisicao) {
        if (modoDemonstracao) {
            exigirAssistente(requisicao);
            return;
        }
        if (requisicao.getUserPrincipal() instanceof OAuth2AuthenticationToken oauth && oauth.isAuthenticated()) {
            String email = oauth.getPrincipal().getAttribute("email");
            Boolean verificado = oauth.getPrincipal().getAttribute("email_verified");
            if (Boolean.TRUE.equals(verificado) && email != null && Arrays.stream(assistentes.split(","))
                    .map(String::trim).anyMatch(item -> item.equalsIgnoreCase(email))) return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas assistentes autorizadas podem administrar formulários.");
    }


    public ContextoAcesso contexto(HttpServletRequest requisicao) {
        String papel = requisicao.getHeader("X-Papel");
        if (papel == null || papel.isBlank()) {
            papel = "ASSISTENTE";
        }

        UUID triboId = null;
        String triboInformada = requisicao.getHeader("X-Tribo-Id");
        if (triboInformada != null && !triboInformada.isBlank()) {
            try {
                triboId = UUID.fromString(triboInformada);
            } catch (IllegalArgumentException excecao) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O identificador da tribo é inválido.");
            }
        }

        return new ContextoAcesso(papel.toUpperCase(), triboId);
    }

    public void exigirAssistente(HttpServletRequest requisicao) {
        if (!contexto(requisicao).assistente()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas assistentes podem executar esta ação.");
        }
    }

    public ContextoAcesso exigirCoordenador(HttpServletRequest requisicao) {
        ContextoAcesso contexto = contexto(requisicao);
        if (!contexto.coordenador()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas coordenadores podem enviar este relatório.");
        }
        if (contexto.triboId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O coordenador precisa estar vinculado a uma tribo.");
        }
        return contexto;
    }

    public record ContextoAcesso(String papel, UUID triboId) {
        public boolean assistente() {
            return "ASSISTENTE".equals(papel);
        }

        public boolean coordenador() {
            return "COORDENADOR".equals(papel);
        }
    }
}
