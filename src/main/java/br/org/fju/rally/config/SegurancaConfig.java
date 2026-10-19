package br.org.fju.rally.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

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
            @Value("${rally.autenticacao.modo-demonstracao:true}") boolean modoDemonstracao,
            AutenticacaoLocalProvider autenticacaoLocal
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
                        resposta.sendRedirect("/entrar");
                    }
                }))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        if (modoDemonstracao) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.GET,
                            "/api/v1/autenticacao/csrf",
                            "/api/v1/autenticacao/sessao",
                            "/api/v1/autenticacao/senha/google").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/autenticacao/login").permitAll()
                    .requestMatchers("/api/v1/publico/formularios/**", "/api/v1/saude", "/oauth2/**", "/login/**").permitAll()
                    .anyRequest().authenticated())
                .oauth2Login(login -> login
                        .successHandler((requisicao, resposta, autenticacao) -> {
                            if (br.org.fju.rally.api.AutenticacaoController.consumirDestinoDefinirSenha(requisicao)) {
                                resposta.sendRedirect("/definir-senha");
                            } else {
                                resposta.sendRedirect("/");
                            }
                        })
                        .failureHandler((requisicao, resposta, excecao) -> resposta.sendRedirect("/entrar?erro_google=1")));
        }

        return http.build();
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
