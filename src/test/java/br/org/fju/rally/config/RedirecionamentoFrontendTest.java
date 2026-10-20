package br.org.fju.rally.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

class RedirecionamentoFrontendTest {

    @Test
    void usaOrigemConfiguradaEmVezDoHostDaRequisicao() throws Exception {
        var resposta = new MockHttpServletResponse();
        new RedirecionamentoFrontend("https://rallydastribos.delcore.com.br/").redirecionar(resposta, "/entrar?erro_google=1");
        assertThat(resposta.getRedirectedUrl()).isEqualTo("https://rallydastribos.delcore.com.br/entrar?erro_google=1");
    }

    @Test
    void mantemRedirecionamentoRelativoSemOrigemConfigurada() throws Exception {
        var resposta = new MockHttpServletResponse();
        new RedirecionamentoFrontend(" ").redirecionar(resposta, "/");
        assertThat(resposta.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void recusaOrigemComCaminhoOuEsquemaInvalido() {
        assertThatIllegalStateException().isThrownBy(() -> new RedirecionamentoFrontend("https://rallydastribos.delcore.com.br/app"));
        assertThatIllegalStateException().isThrownBy(() -> new RedirecionamentoFrontend("javascript:alert(1)"));
        assertThatIllegalStateException().isThrownBy(() -> new RedirecionamentoFrontend("rallydastribos.delcore.com.br"));
    }

    @Test
    void recusaCaminhoQueTrocariaDeHost() {
        var redirecionamento = new RedirecionamentoFrontend("");
        assertThatIllegalArgumentException().isThrownBy(() -> redirecionamento.redirecionar(new MockHttpServletResponse(), "//outro.exemplo"));
        assertThatIllegalArgumentException().isThrownBy(() -> redirecionamento.redirecionar(new MockHttpServletResponse(), "https://outro.exemplo"));
    }
}
