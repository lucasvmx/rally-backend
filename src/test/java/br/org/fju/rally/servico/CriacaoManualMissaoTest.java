package br.org.fju.rally.servico;

import br.org.fju.rally.api.DadosMissao;
import br.org.fju.rally.dominio.Rally;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.RallyRepositorio;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CriacaoManualMissaoTest {
    private final MissaoRepositorio missoes = mock(MissaoRepositorio.class);
    private final RallyRepositorio rallies = mock(RallyRepositorio.class);
    private final AcessoService acesso = mock(AcessoService.class);
    private final MissaoService servico = new MissaoService(missoes, rallies, mock(InterpretadorDeRegras.class),
            mock(InterpretacaoMissoesAssincronaService.class), acesso,
            new ValidacaoConfiguracaoMissaoService(new RegrasPontuacaoService()));
    private final UUID rallyId = UUID.randomUUID();
    private final MockHttpServletRequest requisicao = new MockHttpServletRequest();
    private final LocalDate inicio = LocalDate.of(2026, 1, 1);

    private DadosMissao dados(LocalDate data, boolean foto) {
        return new DadosMissao("Missão", "Descrição", data, data, null, 0, 0, 0, 0, 0, foto,
                "integrante", null, List.of(Map.of("tipo", "POR_RELATORIO", "pontos", 10)),
                List.of(Map.of("id", "local", "titulo", "Local", "tipo", "texto")));
    }

    @Test
    void criaComConfiguracaoCompletaNoRally() {
        when(rallies.findById(rallyId)).thenReturn(Optional.of(new Rally(rallyId, "Rally", inicio, inicio.plusDays(10))));
        when(missoes.saveAndFlush(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
        var resposta = servico.criarManual(rallyId, dados(inicio, false), requisicao);
        assertThat(resposta.rallyId()).isEqualTo(rallyId);
        assertThat(resposta.exigeFoto()).isFalse();
        assertThat(resposta.regrasPontuacao()).containsExactly(Map.of("tipo", "POR_RELATORIO", "pontos", 10));
        assertThat(resposta.camposRelatorio().get(0)).containsEntry("id", "local").containsEntry("escopo", "RELATORIO");
        verify(acesso).exigirAssistente(requisicao);
    }

    @Test
    void rejeitaRallyInexistenteOuDatasForaDoPeriodo() {
        assertThatThrownBy(() -> servico.criarManual(rallyId, dados(inicio, true), requisicao))
                .isInstanceOfSatisfying(ResponseStatusException.class, erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        when(rallies.findById(rallyId)).thenReturn(Optional.of(new Rally(rallyId, "Rally", inicio, inicio.plusDays(10))));
        assertThatThrownBy(() -> servico.criarManual(rallyId, dados(inicio.minusDays(1), true), requisicao))
                .isInstanceOfSatisfying(ResponseStatusException.class, erro -> assertThat(erro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(missoes, never()).saveAndFlush(any());
    }

    @Test
    void acessoNegadoNaoConsultaRally() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(acesso).exigirAssistente(requisicao);
        assertThatThrownBy(() -> servico.criarManual(rallyId, dados(inicio, true), requisicao))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(rallies, missoes);
    }
}
