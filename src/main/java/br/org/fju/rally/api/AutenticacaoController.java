package br.org.fju.rally.api;

import br.org.fju.rally.config.RedirecionamentoFrontend;
import br.org.fju.rally.servico.ContaAcessoService;
import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.UsuarioLocalPrincipal;
import br.org.fju.rally.servico.PapelUsuario;
import br.org.fju.rally.servico.TokenJwtService;
import br.org.fju.rally.servico.UsuarioAcesso;
import br.org.fju.rally.servico.UsuarioAcessoService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/autenticacao")
public class AutenticacaoController {
    private static final String DESTINO_DEFINIR_SENHA = "RALLY_DEFINIR_SENHA_APOS_GOOGLE";

    private final ContaAcessoService contas;
    private final AcessoService acesso;
    private final UsuarioAcessoService usuarios;
    private final TokenJwtService tokens;
    private final RedirecionamentoFrontend redirecionamento;

    public AutenticacaoController(ContaAcessoService contas, AcessoService acesso,
            UsuarioAcessoService usuarios, TokenJwtService tokens, RedirecionamentoFrontend redirecionamento) {
        this.contas = contas;
        this.acesso = acesso;
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.redirecionamento = redirecionamento;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(HttpServletRequest requisicao) {
        Object token = requisicao.getAttribute(CsrfToken.class.getName());
        if (token instanceof CsrfToken csrfToken) csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessao")
    public RespostaSessao sessao(Authentication autenticacao) {
        if (autenticacao == null || !autenticacao.isAuthenticated() || autenticacao instanceof AnonymousAuthenticationToken) {
            return new RespostaSessao(false, null, null, null, false, null, null, null, null, false);
        }
        if (autenticacao instanceof OAuth2AuthenticationToken oauth) {
            String email = oauth.getPrincipal().getAttribute("email");
            boolean verificado = Boolean.TRUE.equals(oauth.getPrincipal().getAttribute("email_verified"));
            String googleSubject = oauth.getPrincipal().getAttribute("sub");
            UsuarioAcesso usuario = usuarios.autenticarGoogle(email, verificado, googleSubject);
            return resposta(usuario, oauth.getPrincipal().getAttribute("name"), "GOOGLE", true,
                    tokens.emitir(usuario, "GOOGLE", googleSubject));
        }
        if (autenticacao.getPrincipal() instanceof UsuarioLocalPrincipal usuario) {
            UsuarioAcesso perfilAtual = usuarios.buscarPorId(usuario.usuario().id());
            if (perfilAtual == null) {
                return new RespostaSessao(false, null, null, null, false, null, null, null, null, false);
            }
            long versaoAtual = contas.versaoCredenciais(perfilAtual.id());
            boolean sessaoAtual = usuario.versaoCredenciais() == versaoAtual;
            boolean trocaPendente = contas.exigeTrocaSenha(perfilAtual.id());
            if (!sessaoAtual || (trocaPendente && !usuario.trocaSenhaObrigatoria())) {
                return new RespostaSessao(false, null, null, null, false, null, null, null, null, false);
            }
            if (trocaPendente) {
                return resposta(perfilAtual, usuario.email(), "LOCAL", true, null, true);
            }
            return resposta(perfilAtual, usuario.email(), "LOCAL", true,
                    tokens.emitir(perfilAtual, "LOCAL", null));
        }
        if (autenticacao instanceof JwtAuthenticationToken jwt) {
            PapelUsuario role = PapelUsuario.valueOf(jwt.getToken().getClaimAsString("role"));
            UUID tribeId = uuid(jwt.getToken().getClaimAsString("tribe_id"));
            UUID nationId = uuid(jwt.getToken().getClaimAsString("nation_id"));
            return new RespostaSessao(true, jwt.getToken().getClaimAsString("email"),
                    jwt.getToken().getClaimAsString("name"), jwt.getToken().getClaimAsString("provider"),
                    Boolean.TRUE.equals(jwt.getToken().getClaim("email_verified")), role, tribeId,
                    nationId, jwt.getToken().getTokenValue(), false);
        }
        return new RespostaSessao(false, null, null, null, false, null, null, null, null, false);
    }

    private RespostaSessao resposta(UsuarioAcesso usuario, String nome, String provedor,
            boolean emailVerificado, String token) {
        return new RespostaSessao(true, usuario.email(), nome == null ? usuario.email() : nome, provedor,
                emailVerificado, usuario.papel(), usuario.triboId(), usuario.nacaoId(), token, false);
    }

    private RespostaSessao resposta(UsuarioAcesso usuario, String nome, String provedor,
            boolean emailVerificado, String token, boolean trocaSenhaObrigatoria) {
        return new RespostaSessao(true, usuario.email(), nome == null ? usuario.email() : nome, provedor,
                emailVerificado, usuario.papel(), usuario.triboId(), usuario.nacaoId(), token,
                trocaSenhaObrigatoria);
    }

    public record RequisicaoTrocaSenhaProvisoria(@NotBlank @Size(min = 12, max = 128) String senha) {}

    private static UUID uuid(String valor) {
        return valor == null || valor.isBlank() ? null : UUID.fromString(valor);
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
        requisicao.getSession(true).setAttribute(DESTINO_DEFINIR_SENHA, true);
        redirecionamento.redirecionar(resposta, "/oauth2/authorization/google");
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

    @PutMapping("/senha-provisoria")
    public ResponseEntity<Void> trocarSenhaProvisoria(@Valid @RequestBody RequisicaoTrocaSenhaProvisoria corpo,
            Authentication autenticacao, HttpServletRequest requisicao) throws ServletException {
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioLocalPrincipal usuario)
                || !usuario.trocaSenhaObrigatoria()
                || usuario.versaoCredenciais() != contas.versaoCredenciais(usuario.usuario().id())
                || !contas.exigeTrocaSenha(usuario.usuario().id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta sessão não possui uma senha provisória pendente de troca.");
        }
        if (corpo.senha().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A nova senha deve ter no máximo 72 bytes em UTF-8.");
        }
        contas.trocarSenhaProvisoria(usuario.usuario().id(), corpo.senha());
        requisicao.logout();
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
