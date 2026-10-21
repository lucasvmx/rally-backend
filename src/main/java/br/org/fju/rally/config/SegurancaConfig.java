package br.org.fju.rally.config;

import br.org.fju.rally.servico.PapelUsuario;
import br.org.fju.rally.servico.UsuarioAcesso;
import br.org.fju.rally.servico.UsuarioAcessoService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
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
            RedirecionamentoFrontend redirecionamento
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
                        .requestMatchers(HttpMethod.GET, "/api/v1/publico/formularios/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/publico/formularios/*/respostas").permitAll()
                        .requestMatchers("/api/v1/saude", "/oauth2/**", "/login/**").permitAll()
                        .anyRequest().hasAnyRole("ASSISTENTE", "COORDENADOR_TRIBO", "COORDENADOR_NACAO", "ADMINISTRADOR_SISTEMA"))
                .oauth2Login(login -> login
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(usuarioGoogle))
                        .successHandler((requisicao, resposta, autenticacao) -> {
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
