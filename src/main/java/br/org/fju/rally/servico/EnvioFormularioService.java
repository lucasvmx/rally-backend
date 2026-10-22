package br.org.fju.rally.servico;

import br.org.fju.rally.api.RequisicaoRelatorio;
import br.org.fju.rally.api.RespostaRelatorio;
import br.org.fju.rally.api.PessoaRelatorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Grava o relatório e a resposta do formulário na mesma transação. */
@Service
public class EnvioFormularioService {
    private final JdbcTemplate banco;
    private final JsonMapper json;
    private final RelatorioService relatorios;
    private final FaceDetectionService detector;
    private final TransactionTemplate transacao;

    public EnvioFormularioService(JdbcTemplate banco, JsonMapper json, RelatorioService relatorios,
                                  FaceDetectionService detector, PlatformTransactionManager gerenciadorTransacao) {
        this.banco = banco;
        this.json = json;
        this.relatorios = relatorios;
        this.detector = detector;
        this.transacao = new TransactionTemplate(gerenciadorTransacao);
    }

    public RespostaRelatorio salvar(UUID formularioId, RequisicaoRelatorio requisicao,
                                    Map<String, Object> respostas, byte[] foto, String tipoFoto) {
        FaceDetectionService.Resultado deteccao = foto == null ? null : detector.detectar(foto, tipoFoto);
        return transacao.execute(status -> salvarTransacional(formularioId, requisicao, respostas,
                foto, tipoFoto, deteccao));
    }

    /** Persiste um envio autenticado com a foto processada antes de abrir a transação. */
    public RespostaRelatorio salvarRelatorioComFoto(RequisicaoRelatorio requisicao,
                                                     HttpServletRequest requisicaoHttp,
                                                     byte[] foto, String tipoFoto) {
        FaceDetectionService.Resultado deteccao = detector.detectar(foto, tipoFoto);
        return transacao.execute(status -> {
            RespostaRelatorio relatorio = relatorios.criar(requisicao, true, requisicaoHttp);
            List<PessoaRelatorio> pessoas = requisicao.pessoas() == null ? List.of() : requisicao.pessoas();
            Map<String, Object> respostas = new java.util.LinkedHashMap<>();
            if (requisicao.dados() != null) respostas.putAll(requisicao.dados());
            respostas.put("nomes_integrantes", pessoas.stream()
                    .filter(pessoa -> !categoriaNova(pessoa.categoria()))
                    .map(PessoaRelatorio::nome).collect(java.util.stream.Collectors.joining(", ")));
            respostas.put("nomes_novos_afastados", pessoas.stream()
                    .filter(pessoa -> categoriaNova(pessoa.categoria()))
                    .map(PessoaRelatorio::nome).collect(java.util.stream.Collectors.joining(", ")));
            respostas.put("preenchido_por", "Coordenador autenticado");
            respostas.put("pessoas", pessoas);
            banco.update("""
                    INSERT INTO respostas_formularios(
                        relatorio_id, formulario_id, respostas, definicao, nome_arquivo, tipo_arquivo,
                        arquivo, arquivo_anotado, contagem_rostos)
                    VALUES (?, NULL, ?, ?, 'foto', ?, ?, ?, ?)
                    """,
                relatorio.id(), json.writeValueAsString(respostas),
                json.writeValueAsString(Map.of("campos", List.of())), tipoFoto, foto,
                deteccao.imagemAnotada(), deteccao.contagemRostos());
            return relatorio;
        });
    }

    private static boolean categoriaNova(String categoria) {
        String normalizada = categoria.toUpperCase(java.util.Locale.ROOT);
        return normalizada.contains("NOVO") || normalizada.contains("AFAST");
    }

    private RespostaRelatorio salvarTransacional(UUID formularioId, RequisicaoRelatorio requisicao,
                                                  Map<String, Object> respostas, byte[] foto,
                                                  String tipoFoto, FaceDetectionService.Resultado deteccao) {
        RespostaRelatorio relatorio = relatorios.criarPublico(requisicao, foto != null);
        banco.update("""
                INSERT INTO respostas_formularios(
                    relatorio_id, formulario_id, respostas, nome_arquivo, tipo_arquivo, arquivo,
                    arquivo_anotado, contagem_rostos)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
            relatorio.id(), formularioId, json.writeValueAsString(respostas), foto == null ? null : "foto",
            tipoFoto, foto, deteccao == null ? null : deteccao.imagemAnotada(),
            deteccao == null ? null : deteccao.contagemRostos());
        return relatorio;
    }
}
