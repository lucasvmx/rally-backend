package br.org.fju.rally.servico;

import br.org.fju.rally.api.RequisicaoRelatorio;
import br.org.fju.rally.api.RespostaRelatorio;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/** Grava o relatório e a resposta do formulário na mesma transação. */
@Service
public class EnvioFormularioService {
    private final JdbcTemplate banco;
    private final JsonMapper json;
    private final RelatorioService relatorios;

    public EnvioFormularioService(JdbcTemplate banco, JsonMapper json, RelatorioService relatorios) {
        this.banco = banco;
        this.json = json;
        this.relatorios = relatorios;
    }

    @Transactional
    public RespostaRelatorio salvar(UUID formularioId, RequisicaoRelatorio requisicao,
                                    Map<String, Object> respostas, byte[] foto, String tipoFoto) {
        RespostaRelatorio relatorio = relatorios.criarPublico(requisicao, foto != null);
        banco.update("""
                INSERT INTO respostas_formularios(
                    relatorio_id, formulario_id, respostas, nome_arquivo, tipo_arquivo, arquivo)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            relatorio.id(), formularioId, json.writeValueAsString(respostas), foto == null ? null : "foto",
            tipoFoto, foto);
        return relatorio;
    }
}
