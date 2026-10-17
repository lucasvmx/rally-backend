package br.org.fju.rally.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(
            HttpSecurity http,
            @Value("${rally.autenticacao.modo-demonstracao:true}") boolean modoDemonstracao
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(configuracaoCors()))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        if (modoDemonstracao) {
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/v1/publico/formularios/**", "/api/v1/saude", "/oauth2/**", "/login/**").permitAll()
                    .anyRequest().authenticated())
                .oauth2Login(login -> login.defaultSuccessUrl("/", true));
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
