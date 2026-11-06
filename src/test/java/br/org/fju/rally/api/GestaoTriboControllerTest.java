package br.org.fju.rally.api;

import br.org.fju.rally.dominio.*;
import br.org.fju.rally.repositorio.*;
import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.PapelUsuario;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GestaoTriboControllerTest {
    private final TriboRepositorio tribos = mock(TriboRepositorio.class);
    private final IntegranteTriboRepositorio integrantes = mock(IntegranteTriboRepositorio.class);
    private final RelatorioRepositorio relatorios = mock(RelatorioRepositorio.class);
    private final MissaoRepositorio missoes = mock(MissaoRepositorio.class);
    private final AcessoService acesso = mock(AcessoService.class);
    private final HttpServletRequest req = mock(HttpServletRequest.class);
    private final GestaoTriboController controller = new GestaoTriboController(tribos, integrantes, relatorios, missoes, acesso);
    private final UUID triboId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();
    private final Tribo tribo = new Tribo(triboId, "Judá", new Nacao(UUID.randomUUID(), "Nação"), true);

    @Test void impedeAcessoForaDaTribo() {
        when(tribos.findById(triboId)).thenReturn(Optional.of(tribo));
        when(acesso.contexto(req)).thenReturn(new AcessoService.ContextoAcesso(PapelUsuario.COORDENADOR_TRIBO, UUID.randomUUID(), null, usuarioId));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.listar(triboId, req)).getStatusCode().value());
        verifyNoInteractions(integrantes);
    }

    @Test void bloqueiaContaNaoAutenticadaEOutraNacao() {
        when(acesso.contexto(req)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED));
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> controller.listar(triboId, req)).getStatusCode().value());
        doReturn(new AcessoService.ContextoAcesso(PapelUsuario.COORDENADOR_NACAO, null, UUID.randomUUID(), usuarioId)).when(acesso).contexto(req);
        when(tribos.findById(triboId)).thenReturn(Optional.of(tribo));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> controller.listar(triboId, req)).getStatusCode().value());
    }

    @Test void naoPermiteExcluirIntegranteDeOutraTribo() {
        when(tribos.findById(triboId)).thenReturn(Optional.of(tribo));
        when(acesso.contexto(req)).thenReturn(new AcessoService.ContextoAcesso(PapelUsuario.COORDENADOR_TRIBO, triboId, null, usuarioId));
        UUID outroIntegrante = UUID.randomUUID();
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> controller.excluir(triboId, outroIntegrante, req)).getStatusCode().value());
        verify(integrantes, never()).delete(any());
    }

    @Test void coordenadorVeApenasRelatoriosDeSuaAutoria() {
        when(tribos.findById(triboId)).thenReturn(Optional.of(tribo));
        when(acesso.contexto(req)).thenReturn(new AcessoService.ContextoAcesso(PapelUsuario.COORDENADOR_TRIBO, triboId, null, usuarioId));
        var missao = new Missao(UUID.randomUUID(), "Missão", "", LocalDate.now(), LocalDate.now(), null, 0, 0, 0, 0, 0, false, "integrante", "ATIVA", 1);
        var proprio = new Relatorio(UUID.randomUUID(), missao, tribo, 0, 0, "", Instant.now(), false, "ENVIADO", 0, null);
        proprio.definirAutorId(usuarioId);
        var legado = new Relatorio(UUID.randomUUID(), missao, tribo, 0, 0, "", Instant.now(), false, "ENVIADO", 0, null);
        when(relatorios.buscarPorTriboEAutor(triboId, usuarioId)).thenReturn(List.of(proprio));
        assertEquals(List.of(proprio.getId()), controller.relatorios(triboId, req).stream().map(RespostaRelatorio::id).toList());
    }
}
