package br.org.fju.rally.servico;

import br.org.fju.rally.dominio.Rally;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.RallyRepositorio;
import br.org.fju.rally.repositorio.RelatorioRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    private final RallyRepositorio rallies = mock(RallyRepositorio.class);
    private final MissaoRepositorio missoes = mock(MissaoRepositorio.class);
    private final RelatorioRepositorio relatorios = mock(RelatorioRepositorio.class);
    private final PontuacaoService pontuacao = mock(PontuacaoService.class);
    private final AcessoService acesso = mock(AcessoService.class);
    private final HttpServletRequest requisicao = mock(HttpServletRequest.class);
    private final DashboardService service = new DashboardService(rallies, missoes, relatorios, pontuacao, acesso);

    @Test
    void aplicaEscopoEContaSomentePessoasAprovadas() {
        UUID rallyId = UUID.randomUUID(), triboId = UUID.randomUUID();
        var rally = new Rally(rallyId, "Rally", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        when(acesso.contexto(requisicao)).thenReturn(new AcessoService.ContextoAcesso(PapelUsuario.COORDENADOR_TRIBO, triboId, null));
        when(rallies.findById(rallyId)).thenReturn(Optional.of(rally));
        when(missoes.countByRallyId(rallyId)).thenReturn(3L);
        var aprovado = grupo("APROVADO", 2, 7, 4);
        var enviado = grupo("ENVIADO", 1, 100, 100);
        when(relatorios.resumoDashboard(rallyId, false, triboId, null)).thenReturn(List.of(aprovado, enviado));
        var resposta = service.consultar(rallyId, requisicao);
        assertEquals(3, resposta.missoes().total());
        assertEquals(3, resposta.relatorios().total());
        assertEquals(1, resposta.relatorios().enviados());
        assertEquals(2, resposta.relatorios().aprovados());
        assertEquals(7, resposta.relatorios().pessoasAprovadas());
        assertEquals(4, resposta.relatorios().novosAfastadosAprovados());
        verify(relatorios).resumoDashboard(rallyId, false, triboId, null);
    }

    @Test
    void semRallyAtivoRetornaResumoVazio() {
        when(acesso.contexto(requisicao)).thenReturn(new AcessoService.ContextoAcesso(PapelUsuario.ASSISTENTE, null, null));
        assertNull(service.consultar(null, requisicao).rally());
        verifyNoInteractions(relatorios, missoes, pontuacao);
    }

    @Test
    void selecionaRallyAtivoMaisRecenteERejeitaIdInexistente() {
        when(acesso.contexto(requisicao)).thenReturn(new AcessoService.ContextoAcesso(PapelUsuario.ASSISTENTE, null, null));
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Rally antigo = new Rally(UUID.randomUUID(), "Antigo", hoje.minusDays(2), hoje.plusDays(1));
        Rally recente = new Rally(UUID.randomUUID(), "Recente", hoje.minusDays(1), hoje.plusDays(1));
        when(rallies.findAll()).thenReturn(List.of(antigo, recente));
        assertEquals(recente.getId(), service.consultar(null, requisicao).rally().id());
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.consultar(UUID.randomUUID(), requisicao)).getStatusCode().value());
    }

    private RelatorioRepositorio.ResumoDashboard grupo(String status, long total, long pessoas, long novos) {
        var grupo = mock(RelatorioRepositorio.ResumoDashboard.class);
        when(grupo.getStatus()).thenReturn(status);
        when(grupo.getTotal()).thenReturn(total);
        if (status.equals("APROVADO")) {
            when(grupo.getPessoas()).thenReturn(pessoas);
            when(grupo.getNovos()).thenReturn(novos);
        }
        return grupo;
    }
}
