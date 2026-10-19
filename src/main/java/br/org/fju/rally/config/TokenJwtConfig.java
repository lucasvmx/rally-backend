package br.org.fju.rally.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class TokenJwtConfig {
    @Bean
    SecretKey chaveJwt(@Value("${rally.autenticacao.jwt-segredo}") String segredoBase64) {
        byte[] segredo;
        try {
            segredo = Base64.getDecoder().decode(segredoBase64);
        } catch (IllegalArgumentException excecao) {
            throw new IllegalStateException("RALLY_JWT_SECRET deve conter uma chave em Base64.", excecao);
        }
        if (segredo.length < 32) {
            throw new IllegalStateException("RALLY_JWT_SECRET deve decodificar para pelo menos 32 bytes.");
        }
        return new SecretKeySpec(segredo, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey chave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey chave,
                          @Value("${rally.autenticacao.jwt-emissor}") String emissor,
                          @Value("${rally.autenticacao.jwt-audiencia}") String audiencia) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(chave)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> validadorAudiencia = jwt -> jwt.getAudience().contains(audiencia)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiência JWT inválida.", null));
        decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(emissor), validadorAudiencia));
        return decoder;
    }
}
