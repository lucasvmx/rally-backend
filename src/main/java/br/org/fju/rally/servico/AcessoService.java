package br.org.fju.rally.servico;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AcessoService {
    private final UsuarioAcessoService usuarios;

    @Autowired
    public AcessoService(UsuarioAcessoService usuarios) {
        this.usuarios = usuarios;
    }

    public AcessoService() {
        this.usuarios = null;
    }

    public void exigirAdministracaoFormularios(HttpServletRequest requisicao) {
        ContextoAcesso contexto = contexto(requisicao);
        if (!contexto.assistente() && !contexto.administradorSistema()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas assistentes ou administradores do sistema podem administrar formulários.");
        }
    }

    public String emailAssistenteVerificada(Authentication autenticacao) {
        String email;
        boolean verificado;
        String subject;
        if (autenticacao instanceof OAuth2AuthenticationToken oauth) {
            email = oauth.getPrincipal().getAttribute("email");
            verificado = Boolean.TRUE.equals(oauth.getPrincipal().getAttribute("email_verified"));
            subject = oauth.getPrincipal().getAttribute("sub");
        } else if (autenticacao instanceof JwtAuthenticationToken jwt
                && "GOOGLE".equals(jwt.getToken().getClaimAsString("provider"))) {
            email = jwt.getToken().getClaimAsString("email");
            verificado = Boolean.TRUE.equals(jwt.getToken().getClaim("email_verified"));
            subject = jwt.getToken().getClaimAsString("google_sub");
        } else {
            return null;
        }
        if (!verificado || usuarios == null) return null;
        UsuarioAcesso usuario = usuarios.autenticarGoogle(email, true, subject);
        return usuario == null || usuario.papel() != PapelUsuario.ASSISTENTE ? null : usuario.email();
    }

    public ContextoAcesso contexto(HttpServletRequest requisicao) {
        Authentication autenticacao = requisicao.getUserPrincipal() instanceof Authentication atual ? atual : null;
        if (autenticacao == null || !autenticacao.isAuthenticated()
                || autenticacao instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária.");
        }

        if (autenticacao instanceof JwtAuthenticationToken jwtAutenticacao) {
            return contextoJwt(jwtAutenticacao.getToken());
        }
        if (autenticacao.getPrincipal() instanceof UsuarioLocalPrincipal local) {
            UsuarioAcesso usuario = usuarios == null ? local.usuario() : usuarios.buscarPorId(local.usuario().id());
            if (usuario == null) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A conta não possui mais um papel autorizado.");
            }
            return new ContextoAcesso(usuario.papel(), usuario.triboId(), usuario.nacaoId());
        }
        if (autenticacao instanceof OAuth2AuthenticationToken oauth && usuarios != null) {
            String email = oauth.getPrincipal().getAttribute("email");
            Boolean verificado = oauth.getPrincipal().getAttribute("email_verified");
            String subject = oauth.getPrincipal().getAttribute("sub");
            UsuarioAcesso usuario = usuarios.autenticarGoogle(email, Boolean.TRUE.equals(verificado), subject);
            return new ContextoAcesso(usuario.papel(), usuario.triboId(), usuario.nacaoId());
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A identidade autenticada não possui um papel Rally.");
    }

    public void exigirAssistente(HttpServletRequest requisicao) {
        ContextoAcesso contexto = contexto(requisicao);
        if (!contexto.assistente() && !contexto.administradorSistema()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas assistentes ou administradores do sistema podem executar esta ação.");
        }
    }

    public void exigirAdministradorSistema(HttpServletRequest requisicao) {
        if (!contexto(requisicao).administradorSistema()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas administradores do sistema podem alterar papéis de usuários.");
        }
    }

    public ContextoAcesso exigirCoordenador(HttpServletRequest requisicao) {
        ContextoAcesso contexto = contexto(requisicao);
        if (!contexto.coordenador()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apenas coordenadores de tribo ou nação podem enviar este relatório.");
        }
        return contexto;
    }

    private ContextoAcesso contextoJwt(Jwt jwt) {
        try {
            PapelUsuario papel = PapelUsuario.valueOf(jwt.getClaimAsString("role"));
            UUID triboId = uuid(jwt.getClaimAsString("tribe_id"));
            UUID nacaoId = uuid(jwt.getClaimAsString("nation_id"));
            UUID.fromString(jwt.getSubject());
            if (papel == PapelUsuario.COORDENADOR_TRIBO && (triboId == null || nacaoId != null)
                    || papel == PapelUsuario.COORDENADOR_NACAO && (nacaoId == null || triboId != null)
                    || (papel == PapelUsuario.ASSISTENTE || papel == PapelUsuario.ADMINISTRADOR_SISTEMA)
                    && (triboId != null || nacaoId != null)) {
                throw new IllegalArgumentException("Escopo incompatível com o papel.");
            }
            return new ContextoAcesso(papel, triboId, nacaoId);
        } catch (RuntimeException excecao) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "O JWT não contém um papel ou escopo válido.");
        }
    }

    private static UUID uuid(String valor) {
        return valor == null || valor.isBlank() ? null : UUID.fromString(valor);
    }

    public record ContextoAcesso(PapelUsuario papel, UUID triboId, UUID nacaoId) {
        public boolean assistente() {
            return papel == PapelUsuario.ASSISTENTE;
        }

        public boolean administradorSistema() {
            return papel == PapelUsuario.ADMINISTRADOR_SISTEMA;
        }

        public boolean coordenador() {
            return papel == PapelUsuario.COORDENADOR_TRIBO || papel == PapelUsuario.COORDENADOR_NACAO;
        }

        public boolean privilegiado() {
            return assistente() || administradorSistema();
        }

        public boolean podeAcessarTribo(UUID triboId, UUID nacaoDaTribo) {
            if (privilegiado()) return true;
            if (papel == PapelUsuario.COORDENADOR_TRIBO) return this.triboId != null && this.triboId.equals(triboId);
            return papel == PapelUsuario.COORDENADOR_NACAO && nacaoId != null && nacaoId.equals(nacaoDaTribo);
        }
    }
}
