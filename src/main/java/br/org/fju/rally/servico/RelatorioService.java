package br.org.fju.rally.servico;

import br.org.fju.rally.api.RequisicaoRelatorio;
import br.org.fju.rally.api.RespostaRelatorio;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.dominio.Tribo;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.RelatorioRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RelatorioService {
    private final RelatorioRepositorio relatorioRepositorio;
    private final MissaoRepositorio missaoRepositorio;
    private final TriboRepositorio triboRepositorio;
    private final PontuacaoService pontuacaoService;
    private final AcessoService acessoService;
    private final Clock relogio;

    public RelatorioService(RelatorioRepositorio relatorioRepositorio,
                            MissaoRepositorio missaoRepositorio,
                            TriboRepositorio triboRepositorio,
                            PontuacaoService pontuacaoService,
                            AcessoService acessoService) {
        this.relatorioRepositorio = relatorioRepositorio;
        this.missaoRepositorio = missaoRepositorio;
        this.triboRepositorio = triboRepositorio;
        this.pontuacaoService = pontuacaoService;
        this.acessoService = acessoService;
        this.relogio = Clock.systemDefaultZone();
    }

    @Transactional(readOnly = true)
    public List<RespostaRelatorio> listar(HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso acesso = acessoService.contexto(requisicao);
        return relatorioRepositorio.buscarComReferencias().stream()
                .filter(relatorio -> acesso.assistente()
                        || (acesso.coordenador() && acesso.triboId() != null
                        && acesso.triboId().equals(relatorio.getTribo().getId())))
                .map(RespostaRelatorio::de)
                .toList();
    }

    @Transactional
    public RespostaRelatorio criar(RequisicaoRelatorio corpo, HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso acesso = acessoService.exigirCoordenador(requisicao);
        if (!acesso.triboId().equals(corpo.triboId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O coordenador só pode enviar relatório para a própria tribo.");
        }

        return criarPublico(corpo);
    }

    @Transactional
    public RespostaRelatorio criarPublico(RequisicaoRelatorio corpo) {
        Missao missao = missaoRepositorio.findById(corpo.missaoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Missão não encontrada."));
        Tribo tribo = triboRepositorio.findById(corpo.triboId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tribo não encontrada."));

        LocalDateTime agora = LocalDateTime.now(relogio);
        boolean atrasado = missao.getPrazoEnvio() != null && agora.isAfter(missao.getPrazoEnvio());
        Relatorio relatorio = new Relatorio(
                UUID.randomUUID(), missao, tribo, corpo.quantidadePessoas(), corpo.quantidadeNovosAfastados(),
                corpo.observacoes(), agora, atrasado, "ENVIADO", 0, null);
        return RespostaRelatorio.de(relatorioRepositorio.saveAndFlush(relatorio));
    }

    @Transactional
    public RespostaRelatorio aprovar(UUID id, String justificativa, HttpServletRequest requisicao) {
        acessoService.exigirAssistente(requisicao);
        Relatorio relatorio = buscar(id);
        relatorio.aprovar(pontuacaoService.calcularPontos(relatorio), justificativa);
        return RespostaRelatorio.de(relatorioRepositorio.save(relatorio));
    }

    @Transactional
    public RespostaRelatorio rejeitar(UUID id, String justificativa, HttpServletRequest requisicao) {
        acessoService.exigirAssistente(requisicao);
        Relatorio relatorio = buscar(id);
        relatorio.rejeitar(justificativa);
        return RespostaRelatorio.de(relatorioRepositorio.save(relatorio));
    }

    private Relatorio buscar(UUID id) {
        return relatorioRepositorio.buscarComReferencias().stream()
                .filter(relatorio -> relatorio.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relatório não encontrado."));
    }
}
