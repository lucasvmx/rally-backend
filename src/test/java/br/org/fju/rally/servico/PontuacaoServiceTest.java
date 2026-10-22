package br.org.fju.rally.servico;

import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.dominio.Tribo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PontuacaoServiceTest {

    @Test
    void calculaPontosDaMissaoComBonusCumulativoDeNovos() {
        Missao missao = new Missao(
                UUID.randomUUID(), "Mega Help", "", LocalDate.now(), LocalDate.now(), Instant.now(),
                4000, 5000, 25000, 0, 0, true, "integrante", "PUBLICADA", 1);
        Relatorio relatorio = new Relatorio(
                UUID.randomUUID(), missao, null, 10, 6, null, Instant.now(), false,
                "ENVIADO", 0, null);

        PontuacaoService servico = new PontuacaoService(null, null, null);

        assertThat(servico.calcularPontos(relatorio)).isEqualTo(10 * 4000 + 6 * 5000 + 25000);
    }

    @Test
    void naoAplicaBonusParcialAntesDeCompletarCincoNovos() {
        Missao missao = new Missao(
                UUID.randomUUID(), "Mega Help", "", LocalDate.now(), LocalDate.now(), Instant.now(),
                4000, 5000, 25000, 0, 0, true, "integrante", "PUBLICADA", 1);
        Relatorio relatorio = new Relatorio(
                UUID.randomUUID(), missao, null, 10, 4, null, Instant.now(), false,
                "ENVIADO", 0, null);

        PontuacaoService servico = new PontuacaoService(null, null, null);

        assertThat(servico.calcularPontos(relatorio)).isEqualTo(10 * 4000 + 4 * 5000);
    }
}
