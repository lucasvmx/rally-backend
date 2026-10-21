package br.org.fju.rally.config;

import br.org.fju.rally.servico.PapelUsuario;
import br.org.fju.rally.servico.ContaAcessoService;
import br.org.fju.rally.servico.UsuarioAcesso;
import br.org.fju.rally.servico.UsuarioLocalPrincipal;
import br.org.fju.rally.servico.UsuarioAcessoService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SegurancaConfig {

    @Value("${rally.cors.origem-frontend:}")
    private String origemFrontend = "";

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean cookieSeguro;

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(
            HttpSecurity http,
            AutenticacaoLocalProvider autenticacaoLocal,
            OAuth2UserService<OidcUserRequest, OidcUser> usuarioGoogle,
            Converter<Jwt, ? extends AbstractAuthenticationToken> autenticacaoJwt,
            RedirecionamentoFrontend redirecionamento,
            UsuarioAcessoService usuarios,
            ContaAcessoService contas
    ) throws Exception {
        CookieCsrfTokenRepository repositorioCsrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repositorioCsrf.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(cookieSeguro));
        http
                .cors(cors -> cors.configurationSource(configuracaoCors()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(repositorioCsrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authenticationProvider(autenticacaoLocal)
                .exceptionHandling(excecoes -> excecoes.authenticationEntryPoint((requisicao, resposta, erro) -> {
                    if (requisicao.getRequestURI().startsWith("/api/")) {
                        resposta.setStatus(401);
                        resposta.setContentType("application/json;charset=UTF-8");
                        resposta.getWriter().write("{\"mensagem\":\"Autenticação necessária.\"}");
                    } else {
                        redirecionamento.redirecionar(resposta, "/entrar");
                    }
                }))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/autenticacao/csrf",
                                "/api/v1/autenticacao/sessao",
                                "/api/v1/autenticacao/senha/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/autenticacao/login").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/autenticacao/senha-provisoria").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/autenticacao/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/publico/formularios/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/publico/formularios/*/respostas").permitAll()
                        .requestMatchers("/api/v1/saude", "/oauth2/**", "/login/**").permitAll()
                        .anyRequest().access((autenticacao, contexto) ->
                                autorizarContaAtual(autenticacao.get(), contexto.getRequest(), usuarios, contas)))
                .oauth2Login(login -> login
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(usuarioGoogle))
                        .successHandler((requisicao, resposta, autenticacao) -> {
                            if (autenticacao instanceof OAuth2AuthenticationToken oauth) {
                                String googleSubject = oauth.getPrincipal().getAttribute("sub");
                                if (googleSubject == null) googleSubject = oauth.getPrincipal().getName();
                                if (googleSubject != null) usuarios.registrarLoginGoogle(googleSubject);
                            }
                            if (br.org.fju.rally.api.AutenticacaoController.consumirDestinoDefinirSenha(requisicao)) {
                                redirecionamento.redirecionar(resposta, "/definir-senha");
                            } else {
                                redirecionamento.redirecionar(resposta, "/");
                            }
                        })
                        .failureHandler((requisicao, resposta, excecao) -> redirecionamento.redirecionar(resposta, "/entrar?erro_google=1")))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(autenticacaoJwt)));

        return http.build();
    }

    private AuthorizationDecision autorizarContaAtual(Authentication autenticacao,
            jakarta.servlet.http.HttpServletRequest requisicao, UsuarioAcessoService usuarios,
            ContaAcessoService contas) {
        if (autenticacao == null || !autenticacao.isAuthenticated()) return new AuthorizationDecision(false);
        UUID usuarioId = null;
        boolean sessaoLocal = false;
        UsuarioLocalPrincipal principalLocal = null;
        if (autenticacao.getPrincipal() instanceof UsuarioLocalPrincipal local) {
            usuarioId = local.usuario().id();
            sessaoLocal = true;
            principalLocal = local;
        } else if (autenticacao instanceof JwtAuthenticationToken jwt) {
            try {
                usuarioId = UUID.fromString(jwt.getToken().getSubject());
            } catch (RuntimeException ignorada) {
                return new AuthorizationDecision(false);
            }
        } else if (autenticacao instanceof OAuth2AuthenticationToken oauth) {
            String subject = oauth.getPrincipal().getAttribute("sub");
            UsuarioAcesso usuario = subject == null ? null : usuarios.buscarPorGoogleSubject(subject);
            if (usuario == null) return new AuthorizationDecision(false);
            usuarioId = usuario.id();
        }
        if (usuarioId == null) return new AuthorizationDecision(false);
        UsuarioAcesso usuario = usuarios.buscarPorId(usuarioId);
        if (usuario == null) return new AuthorizationDecision(false);

        boolean trocaObrigatoria = contas.exigeTrocaSenha(usuarioId);
        String caminho = requisicao.getRequestURI();
        boolean rotaTrocaProvisoria = "PUT".equals(requisicao.getMethod())
                && "/api/v1/autenticacao/senha-provisoria".equals(caminho);
        boolean rotaLogout = "POST".equals(requisicao.getMethod())
                && "/api/v1/autenticacao/logout".equals(caminho);
        long versaoAtual = usuarios.versaoCredenciais(usuarioId);
        if (sessaoLocal && principalLocal.versaoCredenciais() != versaoAtual) {
            return new AuthorizationDecision(rotaLogout);
        }
        if (trocaObrigatoria) {
            boolean sessaoProvisoriaVigente = sessaoLocal && principalLocal.trocaSenhaObrigatoria()
                    && usuario.tipoConta() == br.org.fju.rally.servico.TipoContaUsuario.LOCAL;
            return new AuthorizationDecision(rotaLogout || (sessaoProvisoriaVigente && rotaTrocaProvisoria));
        }
        return new AuthorizationDecision(true);
    }

    @Bean
    OAuth2UserService<OidcUserRequest, OidcUser> usuarioGoogle(UsuarioAcessoService usuarios) {
        OidcUserService padrao = new OidcUserService();
        return requisicao -> {
            OidcUser usuarioGoogle = padrao.loadUser(requisicao);
            UsuarioAcesso usuario = usuarios.autenticarGoogle(usuarioGoogle.getEmail(),
                    Boolean.TRUE.equals(usuarioGoogle.getEmailVerified()), usuarioGoogle.getSubject());
            Set<GrantedAuthority> autoridades = new HashSet<>(usuarioGoogle.getAuthorities());
            autoridades.add(new SimpleGrantedAuthority("ROLE_" + usuario.papel().name()));
            return new DefaultOidcUser(autoridades, usuarioGoogle.getIdToken(), usuarioGoogle.getUserInfo(), "sub");
        };
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> autenticacaoJwt() {
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(jwt -> {
            String papel = jwt.getClaimAsString("role");
            try {
                PapelUsuario.valueOf(papel);
                return List.of(new SimpleGrantedAuthority("ROLE_" + papel));
            } catch (RuntimeException excecao) {
                return List.of();
            }
        });
        return conversor;
    }

    @Bean
    CorsConfigurationSource configuracaoCors() {
        CorsConfiguration configuracao = new CorsConfiguration();
        List<String> origensPermitidas = new ArrayList<>(List.of("http://localhost:4200", "http://127.0.0.1:4200"));
        if (!origemFrontend.isBlank()) {
            origensPermitidas.add(origemFrontend.trim());
        }
        configuracao.setAllowedOrigins(origensPermitidas);
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("*"));
        configuracao.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", configuracao);
        return fonte;
    }
}
