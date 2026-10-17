package br.org.fju.rally.api;

import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.RelatorioService;
import org.junit.jupiter.api.Test;
import br.org.fju.rally.dominio.Missao;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FormulariosControllerTest {
    private final JdbcTemplate banco = mock(JdbcTemplate.class);
    private final MissaoRepositorio missoes = mock(MissaoRepositorio.class);
    private final RelatorioService relatorios = mock(RelatorioService.class);
    private final FormulariosController controller = new FormulariosController(banco, JsonMapper.builder().build(),
        new AcessoService(), relatorios, missoes, mock(TriboRepositorio.class));
    private final UUID missao = UUID.randomUUID();
    private final String token = "a".repeat(64);

    @Test
    void geraSegredoCriptograficoDiferenteParaCadaPublicacao() {
        when(missoes.existsById(missao)).thenReturn(true);
        var definicao = new FormulariosController.Definicao(missao, "Relatório", "", List.of());
        var primeiro = controller.criar(definicao, new MockHttpServletRequest());
        var segundo = controller.criar(definicao, new MockHttpServletRequest());
        assertThat(primeiro.token()).matches("[a-f0-9]{64}").isNotEqualTo(segundo.token());
        assertThat(primeiro.ativo()).isTrue();
    }

    @Test
    void coordenadorNaoPodeCriarFormulario() {
        var req = new MockHttpServletRequest(); req.addHeader("X-Papel", "COORDENADOR");
        assertThatThrownBy(() -> controller.criar(new FormulariosController.Definicao(missao, "Teste", "", List.of()), req))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("assistentes");
        verifyNoInteractions(banco);
    }

    @Test
    void linkInvalidoOuDesativadoNaoRevelaFormulario() {
        assertThatThrownBy(() -> controller.publico("adivinhar")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        when(banco.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<FormulariosController.Formulario>>any(), eq(token))).thenReturn(List.of());
        assertThatThrownBy(() -> controller.publico(token)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verifyNoInteractions(relatorios);
    }

    private void formularioComCampo(String tipo, List<String> opcoes) {
        var campo = new FormulariosController.Campo("pergunta", "Resposta", tipo, true, opcoes);
        var form = new FormulariosController.Formulario(UUID.randomUUID(), token, true,
            new FormulariosController.Definicao(missao, "Teste", "", List.of(campo)));
        when(banco.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<FormulariosController.Formulario>>any(), eq(token))).thenReturn(List.of(form));
    }
    private FormulariosController.Envio envio(UUID missaoId, Map<String, String> respostas) {
        return new FormulariosController.Envio(new RequisicaoRelatorio(missaoId, UUID.randomUUID(), 1, 0, ""), respostas);
    }
    @Test
    void rejeitaMissaoAlteradaPeloVisitante() {
        formularioComCampo("texto", List.of());
        assertThatThrownBy(() -> controller.enviar(token, envio(UUID.randomUUID(), Map.of()), null))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Missão inválida");
        verifyNoInteractions(relatorios);
    }
    @Test
    void envioPublicoCriaRelatorioESalvaRespostas() throws Exception {
        formularioComCampo("texto", List.of());
        var entidade = mock(Missao.class);
        when(missoes.findById(missao)).thenReturn(Optional.of(entidade));
        var id = UUID.randomUUID();
        when(relatorios.criarPublico(any())).thenReturn(new RespostaRelatorio(id, missao, "Missão", UUID.randomUUID(),
            "Tribo", "Nação", 1, 0, "", java.time.LocalDateTime.now(), false, "ENVIADO", 0, null));
        var resultado = controller.enviar(token, envio(missao, Map.of("pergunta", "Centro")), null);
        assertThat(resultado.get("id")).isEqualTo(id);
        verify(banco).update(startsWith("INSERT INTO respostas_formularios"), eq(id), any(UUID.class), contains("Centro"), isNull(), isNull(), isNull());
    }
    @Test
    void fotoObrigatoriaEArquivoDisfarcadoSaoRejeitados() {
        formularioComCampo("texto", List.of());
        var entidade = mock(Missao.class);
        when(missoes.findById(missao)).thenReturn(Optional.of(entidade));
        when(entidade.isExigeFoto()).thenReturn(true);
        assertThatThrownBy(() -> controller.enviar(token, envio(missao, Map.of("pergunta", "Centro")), null)).hasMessageContaining("exige uma foto");
        var falsa = new MockMultipartFile("arquivo", "foto.png", "image/png", "arquivo invalido".getBytes());
        assertThatThrownBy(() -> controller.enviar(token, envio(missao, Map.of("pergunta", "Centro")), falsa)).hasMessageContaining("não corresponde");
        verifyNoInteractions(relatorios);
    }
    @Test
    void validaObrigatoriedadeTiposEOpcoesNoServidor() {
        formularioComCampo("texto", List.of());
        assertThatThrownBy(() -> controller.enviar(token, envio(missao, Map.of()), null)).hasMessageContaining("Confira a resposta");
        formularioComCampo("numero", List.of());
        assertThatThrownBy(() -> controller.enviar(token, envio(missao, Map.of("pergunta", "NaN")), null)).hasMessageContaining("Número inválido");
        formularioComCampo("selecao", List.of("Sim", "Não"));
        assertThatThrownBy(() -> controller.enviar(token, envio(missao, Map.of("pergunta", "Outra")), null)).hasMessageContaining("Opção inválida");
        verifyNoInteractions(relatorios);
    }
}
