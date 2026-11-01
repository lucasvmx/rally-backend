package br.org.fju.rally.servico;

import br.org.fju.rally.api.DadosMissao;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.dominio.Tribo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PontuacaoServiceTest {

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
