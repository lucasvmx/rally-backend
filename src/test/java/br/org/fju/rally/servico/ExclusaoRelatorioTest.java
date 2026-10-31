package br.org.fju.rally.servico;

import br.org.fju.rally.dominio.ArquivoDrive;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.repositorio.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExclusaoRelatorioTest {
    private final RelatorioRepositorio relatorios = mock(RelatorioRepositorio.class);
    private final ArquivoDriveRepositorio arquivos = mock(ArquivoDriveRepositorio.class);
    private final RelatorioService servico = new RelatorioService(relatorios, arquivos,
            mock(MissaoRepositorio.class), mock(TriboRepositorio.class), mock(PessoaPontuadaRepositorio.class),
            mock(PontuacaoService.class), new AcessoService());
    private final UUID id = UUID.randomUUID();

    @Test
    void administradorExcluiRelatorio() {
        Relatorio relatorio = mock(Relatorio.class);
        when(relatorios.findById(id)).thenReturn(Optional.of(relatorio));
        servico.excluir(id, requisicao("ADMINISTRADOR_SISTEMA"));
        verify(relatorios).delete(relatorio);
    }

    @Test
    void usuariosSemPermissaoNaoConsultamNemExcluemRelatorio() {
        for (String papel : List.of("ASSISTENTE", "COORDENADOR_TRIBO", "COORDENADOR_NACAO")) {
            assertThatThrownBy(() -> servico.excluir(id, requisicao(papel)))
                    .isInstanceOfSatisfying(ResponseStatusException.class,
                            erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        assertThatThrownBy(() -> servico.excluir(id, new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(relatorios, arquivos);
    }

    @Test
    void inexistenteRetorna404() {
        assertThatThrownBy(() -> servico.excluir(id, requisicao("ADMINISTRADOR_SISTEMA")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void arquivoLegadoImpedeExclusaoEPreservaReferencia() {
        when(relatorios.findById(id)).thenReturn(Optional.of(mock(Relatorio.class)));
        when(arquivos.findAllByRelatorioId(id)).thenReturn(List.of(mock(ArquivoDrive.class)));
        assertThatThrownBy(() -> servico.excluir(id, requisicao("ADMINISTRADOR_SISTEMA")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(relatorios, never()).delete(any());
    }

    private MockHttpServletRequest requisicao(String papel) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "HS256")
                .subject(UUID.randomUUID().toString()).claim("role", papel);
        if (papel.equals("COORDENADOR_TRIBO")) jwt.claim("tribe_id", UUID.randomUUID().toString());
        if (papel.equals("COORDENADOR_NACAO")) jwt.claim("nation_id", UUID.randomUUID().toString());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(new JwtAuthenticationToken(jwt.build(), List.of()));
        return request;
    }
}
