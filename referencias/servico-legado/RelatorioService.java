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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RelatorioService {
    private final MissaoRepositorio missaoRepositorio;
    private final TriboRepositorio triboRepositorio;
    private final RelatorioRepositorio relatorioRepositorio;
    private final PontuacaoService pontuacaoService;
    private final AcessoService acessoService;
    private final Clock relogio;

    public RelatorioService(MissaoRepositorio missaoRepositorio, TriboRepositorio triboRepositorio,
                            RelatorioRepositorio relatorioRepositorio, PontuacaoService pontuacaoService,
                            AcessoService acessoService) {
        this.missaoRepositorio = missaoRepositorio;
        this.triboRepositorio = triboRepositorio;
        this.relatorioRepositorio = relatorioRepositorio;
        this.pontuacaoService = pontuacaoService;
        this.acessoService = acessoService;
        this.relogio = Clock.systemDefaultZone();
    }

    public List<RespostaRelatorio> listar(HttpServletRequest requisicao) {
        return relatorioRepositorio.buscarComReferencias().stream()
                .filter(relatorio -> acessoService.ehAssistente(requisicao)
                        || requisicao.getHeader("X-Tribo-Id") != null
                        && requisicao.getHeader("X-Tribo-Id").equals(relatorio.getTribo().getId().toString()))
                .map(RespostaRelatorio::de)
                .toList();
    }

    @Transactional
    public RespostaRelatorio criar(RequisicaoRelatorio requisicao, HttpServletRequest contexto) {
        Missao missao = missaoRepositorio.findById(requisicao.missaoId())
                .orElseThrow(() -> new IllegalArgumentException("Missão não encontrada."));
        Tribo tribo = triboRepositorio.findById(requisicao.triboId())
                .orElseThrow(() -> new IllegalArgumentException("Tribo não encontrada."));
        acessoService.verificarTribo(contexto, tribo.getId());
        LocalDateTime agora = LocalDateTime.now(relogio);
        boolean atrasado = missao.getPrazoEnvio() != null && agora.isAfter(missao.getPrazoEnvio());
        Relatorio relatorio = new Relatorio(UUID.randomUUID(), missao, tribo,
                requisicao.quantidadePessoas(), requisicao.quantidadeNovosAfastados(),
                requisicao.observacoes(), agora, atrasado, "ENVIADO");
        return RespostaRelatorio.de(relatorioRepositorio.save(relatorio));
    }

    @Transactional
    public RespostaRelatorio aprovar(UUID id, String justificativa, HttpServletRequest contexto) {
        Relatorio relatorio = obter(id);
        acessoService.verificarRelatorio(contexto, relatorio);
        int pontos = pontuacaoService.calcularPontosBase(relatorio.getMissao(),
                relatorio.getQuantidadePessoas(), relatorio.getQuantidadeNovosAfastados());
        relatorio.aprovar(pontos, justificativa);
        return RespostaRelatorio.de(relatorioRepositorio.save(relatorio));
    }

    @Transactional
    public RespostaRelatorio rejeitar(UUID id, String justificativa, HttpServletRequest contexto) {
        Relatorio relatorio = obter(id);
        acessoService.verificarRelatorio(contexto, relatorio);
        relatorio.rejeitar(justificativa);
        return RespostaRelatorio.de(relatorioRepositorio.save(relatorio));
    }

    public Relatorio obter(UUID id) {
        return relatorioRepositorio.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Relatório não encontrado."));
    }
}
