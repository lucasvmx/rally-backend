package br.org.fju.rally.servico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TokenJwtService {
    private final JwtEncoder encoder;
    private final UsuarioAcessoService usuarios;
    private final String emissor;
    private final String audiencia;

    public TokenJwtService(JwtEncoder encoder, UsuarioAcessoService usuarios,
                           @Value("${rally.autenticacao.jwt-emissor}") String emissor,
                           @Value("${rally.autenticacao.jwt-audiencia}") String audiencia) {
        this.encoder = encoder;
        this.usuarios = usuarios;
        this.emissor = emissor;
        this.audiencia = audiencia;
    }

    public String emitir(UsuarioAcesso usuario, String provedor, String googleSubject) {
        Instant agora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(emissor)
                .audience(List.of(audiencia))
                .subject(usuario.id().toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(2, ChronoUnit.DAYS))
                .id(java.util.UUID.randomUUID().toString())
                .claim("email", usuario.email())
                .claim("email_verified", true)
                .claim("provider", provedor)
                .claim("role", usuario.papel().name())
                .claim("credential_version", usuarios.versaoCredenciais(usuario.id()));
        if (usuario.triboId() != null) claims.claim("tribe_id", usuario.triboId().toString());
        if (usuario.nacaoId() != null) claims.claim("nation_id", usuario.nacaoId().toString());
        if (googleSubject != null && !googleSubject.isBlank()) claims.claim("google_sub", googleSubject);

        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}
