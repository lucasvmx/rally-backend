package br.org.fju.rally.servico;

import br.org.fju.rally.api.RequisicaoPontuacaoTribo;
import br.org.fju.rally.dominio.*;
import br.org.fju.rally.repositorio.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PontuacaoManualServiceTest {
    private final PontuacaoManualRepositorio pontuacoes = mock(PontuacaoManualRepositorio.class);
    private final RallyRepositorio rallies = mock(RallyRepositorio.class);
    private final TriboRepositorio tribos = mock(TriboRepositorio.class);
    private final MissaoRepositorio missoes = mock(MissaoRepositorio.class);
    private final AcessoService acesso = mock(AcessoService.class);
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final PontuacaoManualService service = new PontuacaoManualService(pontuacoes, rallies, tribos, acesso, missoes);
    private final Rally rally = mock(Rally.class);
    private final Tribo tribo = mock(Tribo.class);
    private final Nacao nacao = mock(Nacao.class);
    private final UUID nacaoId = UUID.randomUUID();
    private final UUID triboId = UUID.randomUUID();
    private final UUID rallyId = UUID.randomUUID();

    private void preparar() {
        when(rallies.findAll()).thenReturn(List.of(rally));
        when(rally.contem(any())).thenReturn(true);
        when(rally.getId()).thenReturn(rallyId);
        when(tribos.findById(triboId)).thenReturn(Optional.of(tribo));
        when(tribo.getNacao()).thenReturn(nacao);
        when(nacao.getId()).thenReturn(nacaoId);
    }

    @Test
    void registraSomenteTriboSelecionadaComMissao() {
        preparar();
        UUID missaoId = UUID.randomUUID();
        Missao missao = mock(Missao.class);
        when(missao.getRally()).thenReturn(rally);
        when(missao.getId()).thenReturn(missaoId);
        when(missoes.findById(missaoId)).thenReturn(Optional.of(missao));
        when(pontuacoes.save(any())).thenAnswer(i -> i.getArgument(0));
        var resposta = service.registrarParaTribo(new RequisicaoPontuacaoTribo(nacaoId, triboId, 25, " motivo ", missaoId), request);
        var captor = org.mockito.ArgumentCaptor.forClass(PontuacaoManual.class);
        verify(pontuacoes).save(captor.capture());
        assertEquals(List.of(tribo), captor.getValue().getTribosDestinatarias());
        assertEquals("motivo", resposta.atividade());
        assertEquals(25, resposta.pontosPorTribo());
        assertEquals(missaoId, resposta.missaoId());
        verify(acesso).exigirAssistente(request);
    }

    @Test
    void rejeitaTriboDeOutraNacaoEMissaoDeOutroRally() {
        preparar();
        assertThrows(ResponseStatusException.class, () -> service.registrarParaTribo(
                new RequisicaoPontuacaoTribo(UUID.randomUUID(), triboId, 1, "motivo", null), request));
        UUID missaoId = UUID.randomUUID();
        Missao missao = mock(Missao.class);
        Rally outro = mock(Rally.class);
        when(outro.getId()).thenReturn(UUID.randomUUID());
        when(missao.getRally()).thenReturn(outro);
        when(missoes.findById(missaoId)).thenReturn(Optional.of(missao));
        assertThrows(ResponseStatusException.class, () -> service.registrarParaTribo(
                new RequisicaoPontuacaoTribo(nacaoId, triboId, 1, "motivo", missaoId), request));
        verify(pontuacoes, never()).save(any());
    }

    @Test
    void validaCamposObrigatorios() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new RequisicaoPontuacaoTribo(nacaoId, triboId, 0, " ", null)).isEmpty());
            assertTrue(validator.validate(new RequisicaoPontuacaoTribo(nacaoId, triboId, 1, "motivo", null)).isEmpty());
        }
    }
}
