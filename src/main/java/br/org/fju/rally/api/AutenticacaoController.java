package br.org.fju.rally.api;

import br.org.fju.rally.servico.ContaAcessoService;
import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.UsuarioLocalPrincipal;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/autenticacao")
public class AutenticacaoController {
    private static final String DESTINO_DEFINIR_SENHA = "RALLY_DEFINIR_SENHA_APOS_GOOGLE";

    private final ContaAcessoService contas;
    private final AcessoService acesso;
    private final boolean modoDemonstracao;

    public AutenticacaoController(ContaAcessoService contas, AcessoService acesso,
            @Value("${rally.autenticacao.modo-demonstracao:true}") boolean modoDemonstracao) {
        this.contas = contas;
        this.acesso = acesso;
        this.modoDemonstracao = modoDemonstracao;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(HttpServletRequest requisicao) {
        Object token = requisicao.getAttribute(CsrfToken.class.getName());
        if (token instanceof CsrfToken csrfToken) csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessao")
    public RespostaSessao sessao(Authentication autenticacao) {
        if (modoDemonstracao) return new RespostaSessao(true, null, "Demonstração", "DEMONSTRACAO", true);
        if (autenticacao == null || !autenticacao.isAuthenticated() || autenticacao instanceof AnonymousAuthenticationToken) {
            return new RespostaSessao(false, null, null, null, false);
        }
        if (autenticacao instanceof OAuth2AuthenticationToken oauth) {
            return new RespostaSessao(true, oauth.getPrincipal().getAttribute("email"),
                    oauth.getPrincipal().getAttribute("name"), "GOOGLE",
                    Boolean.TRUE.equals(oauth.getPrincipal().getAttribute("email_verified")));
        }
        if (autenticacao.getPrincipal() instanceof UsuarioLocalPrincipal usuario) {
            return new RespostaSessao(true, usuario.email(), usuario.email(), "LOCAL", true);
        }
        return new RespostaSessao(false, null, null, null, false);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody RequisicaoLogin corpo, HttpServletRequest requisicao) {
        if (corpo.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            return ResponseEntity.badRequest().body(Map.of("mensagem", "A senha informada excede o limite permitido."));
        }
        try {
            requisicao.login(ContaAcessoService.normalizar(corpo.email()), corpo.senha());
            return ResponseEntity.ok(sessao(SecurityContextHolder.getContext().getAuthentication()));
        } catch (ServletException excecao) {
            AuthenticationException autenticacao = causaAutenticacao(excecao);
            if (autenticacao instanceof LockedException) {
                return ResponseEntity.status(HttpStatus.LOCKED)
                        .body(Map.of("mensagem", "Conta bloqueada após três tentativas incorretas. Entre com Google para redefinir a senha e recuperar o acesso."));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("mensagem", "E-mail ou senha incorretos. Após três tentativas, a conta será bloqueada."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest requisicao, HttpServletResponse resposta) throws ServletException {
        requisicao.logout();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/senha/google")
    public void iniciarDefinicaoDeSenha(HttpServletRequest requisicao, HttpServletResponse resposta) throws IOException {
        if (modoDemonstracao) {
            resposta.sendRedirect("/");
            return;
        }
        requisicao.getSession(true).setAttribute(DESTINO_DEFINIR_SENHA, true);
        resposta.sendRedirect("/oauth2/authorization/google");
    }

    public static boolean consumirDestinoDefinirSenha(HttpServletRequest requisicao) {
        HttpSession sessao = requisicao.getSession(false);
        if (sessao == null) return false;
        boolean destino = Boolean.TRUE.equals(sessao.getAttribute(DESTINO_DEFINIR_SENHA));
        sessao.removeAttribute(DESTINO_DEFINIR_SENHA);
        return destino;
    }

    @PutMapping("/senha")
    public ResponseEntity<Void> definirSenha(@Valid @RequestBody RequisicaoDefinirSenha corpo,
            Authentication autenticacao) {
        if (!(autenticacao instanceof OAuth2AuthenticationToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Use uma conta Google verificada para criar, redefinir ou desbloquear a senha local.");
        }
        String email = acesso.emailAssistenteVerificada(autenticacao);
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente assistentes com e-mail Google verificado podem configurar senha local.");
        }
        if (corpo.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter no máximo 72 bytes em UTF-8.");
        }
        contas.definirSenha(email, corpo.senha());
        return ResponseEntity.noContent().build();
    }

    private static AuthenticationException causaAutenticacao(Throwable excecao) {
        Throwable causa = excecao;
        while (causa != null) {
            if (causa instanceof AuthenticationException autenticacao) return autenticacao;
            causa = causa.getCause();
        }
        return null;
    }
}
