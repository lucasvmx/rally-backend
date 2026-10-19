package br.org.fju.rally.servico;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import java.util.Map;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AcessoServiceTest {
    private final AcessoService servico = new AcessoService();

    @Test
    void formularioEmProducaoNaoAceitaPapelForjado() {
        ReflectionTestUtils.setField(servico, "modoDemonstracao", false);
        var req = new MockHttpServletRequest();
        assertThatThrownBy(() -> servico.exigirAdministracaoFormularios(req)).isInstanceOf(ResponseStatusException.class);
    }
    @Test
    void formularioEmProducaoExigePapelDeAssistenteNoUsuarioProvisionado() {
        var usuarios = mock(UsuarioAcessoService.class);
        var acessoGoogle = new AcessoService(usuarios);
        ReflectionTestUtils.setField(acessoGoogle, "modoDemonstracao", false);
        var req = new MockHttpServletRequest();
        var autoridades = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var atributos = Map.<String, Object>of("email", "assistente@example.org", "email_verified", true, "sub", "google-sub");
        var usuario = new DefaultOAuth2User(autoridades, atributos, "email");
        req.setUserPrincipal(new OAuth2AuthenticationToken(usuario, autoridades, "google"));
        when(usuarios.autenticarGoogle("assistente@example.org", true, "google-sub"))
                .thenReturn(new UsuarioAcesso(UUID.randomUUID(), "assistente@example.org", PapelUsuario.ASSISTENTE,
                        null, null, "google-sub"));
        acessoGoogle.exigirAdministracaoFormularios(req);

        when(usuarios.autenticarGoogle("assistente@example.org", true, "google-sub"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> acessoGoogle.exigirAdministracaoFormularios(req)).isInstanceOf(ResponseStatusException.class);
    }
    @Test
    void coordenadorRecebeSomenteSuaTribo() {
        UUID triboId = UUID.randomUUID();
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.setUserPrincipal(jwt("COORDENADOR_TRIBO", triboId));

        AcessoService.ContextoAcesso contexto = servico.exigirCoordenador(requisicao);

        assertThat(contexto.triboId()).isEqualTo(triboId);
        assertThat(contexto.coordenador()).isTrue();
    }

    @Test
    void assistenteNaoPodeSerTratadaComoCoordenadora() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.setUserPrincipal(jwt("ASSISTENTE", null));

        assertThatThrownBy(() -> servico.exigirCoordenador(requisicao))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("coordenadores");
    }

    private static JwtAuthenticationToken jwt(String role, UUID tribeId) {
        Jwt.Builder token = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("role", role);
        if (tribeId != null) token.claim("tribe_id", tribeId.toString());
        return new JwtAuthenticationToken(token.build(),
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
