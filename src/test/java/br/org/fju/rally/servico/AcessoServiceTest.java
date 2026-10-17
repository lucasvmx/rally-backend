package br.org.fju.rally.servico;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Map;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcessoServiceTest {
    private final AcessoService servico = new AcessoService();

    @Test
    void formularioEmProducaoNaoAceitaPapelForjado() {
        ReflectionTestUtils.setField(servico, "modoDemonstracao", false);
        var req = new MockHttpServletRequest(); req.addHeader("X-Papel", "ASSISTENTE");
        assertThatThrownBy(() -> servico.exigirAdministracaoFormularios(req)).isInstanceOf(ResponseStatusException.class);
    }
    @Test
    void formularioEmProducaoExigeEmailVerificadoNaListaDeAssistentes() {
        ReflectionTestUtils.setField(servico, "modoDemonstracao", false);
        ReflectionTestUtils.setField(servico, "assistentes", "assistente@example.org");
        var req = new MockHttpServletRequest();
        var autoridades = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var usuario = new DefaultOAuth2User(autoridades, Map.of("email", "assistente@example.org", "email_verified", true), "email");
        req.setUserPrincipal(new OAuth2AuthenticationToken(usuario, autoridades, "google"));
        servico.exigirAdministracaoFormularios(req);
        ReflectionTestUtils.setField(servico, "assistentes", "outra@example.org");
        assertThatThrownBy(() -> servico.exigirAdministracaoFormularios(req)).isInstanceOf(ResponseStatusException.class);
    }
    @Test
    void coordenadorRecebeSomenteSuaTribo() {
        UUID triboId = UUID.randomUUID();
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader("X-Papel", "COORDENADOR");
        requisicao.addHeader("X-Tribo-Id", triboId.toString());

        AcessoService.ContextoAcesso contexto = servico.exigirCoordenador(requisicao);

        assertThat(contexto.triboId()).isEqualTo(triboId);
        assertThat(contexto.coordenador()).isTrue();
    }

    @Test
    void assistenteNaoPodeSerTratadaComoCoordenadora() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader("X-Papel", "ASSISTENTE");

        assertThatThrownBy(() -> servico.exigirCoordenador(requisicao))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("coordenadores");
    }
}
