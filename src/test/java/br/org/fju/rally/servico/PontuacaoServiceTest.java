package br.org.fju.rally.servico;

import br.org.fju.rally.api.DadosMissao;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.dominio.Tribo;
import br.org.fju.rally.dominio.PontuacaoManual;
import br.org.fju.rally.dominio.Nacao;
import br.org.fju.rally.dominio.Rally;
import br.org.fju.rally.repositorio.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PontuacaoServiceTest {

    @Test
    void descontoAfetaSomenteOPlacarDaTriboDestinataria() {
        var relatorios = mock(RelatorioRepositorio.class);
        var tribos = mock(TriboRepositorio.class);
        var rallies = mock(RallyRepositorio.class);
        var manuais = mock(PontuacaoManualRepositorio.class);
        var rally = mock(Rally.class);
        var nacao = new Nacao(UUID.randomUUID(), "Nação");
        var alvo = new Tribo(UUID.randomUUID(), "Alvo", nacao, true);
        var outra = new Tribo(UUID.randomUUID(), "Outra", nacao, true);
        var rallyId = UUID.randomUUID();
        when(rally.getId()).thenReturn(rallyId);
        when(rallies.findById(rallyId)).thenReturn(java.util.Optional.of(rally));
        when(tribos.findAll()).thenReturn(List.of(alvo, outra));
        when(relatorios.findByStatus("APROVADO")).thenReturn(List.of());
        var desconto = new PontuacaoManual(UUID.randomUUID(), rally, nacao, alvo, "penalidade", -25,
                "assistente", Instant.now(), List.of(alvo));
        when(manuais.findAllByRallyId(rallyId)).thenReturn(List.of(desconto));
        var servico = new PontuacaoService(relatorios, tribos, null, rallies, null,
                new RegrasPontuacaoService(), manuais);
        var placar = servico.gerarPlacar(rallyId);
        assertThat(placar).anySatisfy(item -> { assertThat(item.triboId()).isEqualTo(alvo.getId()); assertThat(item.pontos()).isEqualTo(-25); });
        assertThat(placar).anySatisfy(item -> { assertThat(item.triboId()).isEqualTo(outra.getId()); assertThat(item.pontos()).isZero(); });
    }

    @Test
    void naoAplicaBonusLegadoSemRegraDinamica() {
        Missao missao = new Missao(
                UUID.randomUUID(), "Mega Help", "", LocalDate.now(), LocalDate.now(), Instant.now(),
                4000, 5000, 25000, 0, 0, true, "integrante", "PUBLICADA", 1);
        Relatorio relatorio = new Relatorio(
                UUID.randomUUID(), missao, null, 10, 6, null, Instant.now(), false,
                "ENVIADO", 0, null);

        PontuacaoService servico = new PontuacaoService(null, null, null);

        assertThat(servico.calcularPontos(relatorio)).isEqualTo(10 * 4000 + 6 * 5000);
    }

    @Test
    void calculaPontosSemBonusParaQuatroNovos() {
        Missao missao = new Missao(
                UUID.randomUUID(), "Mega Help", "", LocalDate.now(), LocalDate.now(), Instant.now(),
                4000, 5000, 25000, 0, 0, true, "integrante", "PUBLICADA", 1);
        Relatorio relatorio = new Relatorio(
                UUID.randomUUID(), missao, null, 10, 4, null, Instant.now(), false,
                "ENVIADO", 0, null);

        PontuacaoService servico = new PontuacaoService(null, null, null);

        assertThat(servico.calcularPontos(relatorio)).isEqualTo(10 * 4000 + 4 * 5000);
    }
    @Test
    void ignoraCampoLegadoEMantemBonusConfiguradoDinamicamente() {
        DadosMissao dados = new DadosMissao("Missão", "", LocalDate.now(), LocalDate.now(), null,
                0, 0, 25000, 0, 0, false, "integrante", null);
        RegrasPontuacaoService servico = new RegrasPontuacaoService();
        assertThat(servico.normalizar(List.of(), dados)).isEmpty();

        Map<String, Object> bonus = Map.of("tipo", "A_CADA", "campo", "novos_afastados",
                "quantidade", 3, "pontos", 100, "escopo", "TRIBO");
        Map<String, Object> regra = servico.normalizar(List.of(bonus), dados).get(0);
        assertThat(servico.calcularBonusAgregado(regra, BigDecimal.valueOf(7))).isEqualTo(200);
    }

}
