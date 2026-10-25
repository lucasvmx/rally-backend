package br.org.fju.rally.servico;

import br.org.fju.rally.api.DadosMissao;
import br.org.fju.rally.api.DadosRally;
import br.org.fju.rally.api.RequisicaoCriarMissoes;
import br.org.fju.rally.api.RequisicaoInterpretacao;
import br.org.fju.rally.api.RespostaInterpretacao;
import br.org.fju.rally.api.RespostaInterpretacaoAssincrona;
import br.org.fju.rally.api.RespostaMissao;
import br.org.fju.rally.api.RespostaRally;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.Rally;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.RallyRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

@Service
public class MissaoService {
    private final MissaoRepositorio missoes;
    private final RallyRepositorio rallies;
    private final InterpretadorDeRegras interpretador;
    private final InterpretacaoMissoesAssincronaService interpretacoesAssincronas;
    private final AcessoService acesso;
    private final ValidacaoConfiguracaoMissaoService validacaoConfiguracao;

    public MissaoService(MissaoRepositorio missoes, RallyRepositorio rallies,
                         InterpretadorDeRegras interpretador,
                         InterpretacaoMissoesAssincronaService interpretacoesAssincronas,
                         AcessoService acesso,
                         ValidacaoConfiguracaoMissaoService validacaoConfiguracao) {
        this.missoes = missoes;
        this.rallies = rallies;
        this.interpretador = interpretador;
        this.interpretacoesAssincronas = interpretacoesAssincronas;
        this.acesso = acesso;
        this.validacaoConfiguracao = validacaoConfiguracao;
    }

    public RespostaInterpretacaoAssincrona iniciarInterpretacao(UUID id, RequisicaoInterpretacao corpo,
                                                                 HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso contexto = acesso.exigirAssistenteEObterContexto(requisicao);
        return interpretacoesAssincronas.iniciar(id, corpo.texto(), contexto.usuarioId());
    }

    public RespostaInterpretacaoAssincrona consultarInterpretacao(UUID id, HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso contexto = acesso.exigirAssistenteEObterContexto(requisicao);
        return interpretacoesAssincronas.consultar(id, contexto.usuarioId());
    }

    public void cancelarInterpretacao(UUID id, HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso contexto = acesso.exigirAssistenteEObterContexto(requisicao);
        interpretacoesAssincronas.cancelar(id, contexto.usuarioId());
    }

    public RespostaInterpretacao interpretarRegras(RequisicaoInterpretacao corpo, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        return interpretador.interpretar(corpo.texto());
    }

    @Transactional
    public List<RespostaMissao> criar(RequisicaoCriarMissoes corpo, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        if (corpo.rallyId() != null && corpo.rally() != null) {
            throw erro("Informe rally_id ou rally, não os dois.");
        }
        Rally rally;
        if (corpo.rallyId() != null) {
            rally = rallies.findById(corpo.rallyId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rally não encontrado."));
            validarDatasMissoesNoRally(rally, corpo.missoes());
        } else {
            DadosRally dadosRally = obterDadosRally(corpo);
            validarCamposRally(dadosRally, corpo.missoes());
            rally = rallies.saveAndFlush(new Rally(UUID.randomUUID(), dadosRally.nome().trim(),
                    dadosRally.dataInicio(), dadosRally.dataFim()));
        }
        List<Missao> novas = corpo.missoes().stream().map(dados -> converter(dados, rally)).toList();
        List<Missao> salvas = missoes.saveAllAndFlush(novas);
        return salvas.stream().map(RespostaMissao::de).toList();
    }

    @Transactional
    public RespostaRally criarRally(DadosRally dados, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        validarDadosRally(dados);
        Rally rally = rallies.saveAndFlush(new Rally(UUID.randomUUID(), dados.nome().trim(),
                dados.dataInicio(), dados.dataFim()));
        LocalDate hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        return RespostaRally.de(rally, hoje);
    }

    @Transactional
    public void excluirMissao(UUID id, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        Missao missao = missoes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Missão não encontrada."));
        missoes.delete(missao);
    }

    @Transactional
    public void excluirRally(UUID id, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        Rally rally = rallies.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rally não encontrado."));
        rallies.delete(rally);
    }

    private Missao converter(DadosMissao dados, Rally rally) {
        if (dados.dataInicio() == null || dados.dataFim() == null) {
            throw erro("Informe as datas de início e fim de todas as missões.");
        }
        if (dados.dataInicio().isAfter(dados.dataFim())) {
            throw erro("A data de início precisa ser anterior ou igual à data de fim.");
        }
        String link = dados.linkGoogleForms() == null || dados.linkGoogleForms().isBlank()
                ? null : dados.linkGoogleForms().trim();
        if (link != null && !InterpretadorDeRegras.linkGoogleFormsValido(link)) {
            throw erro("Use um link HTTPS do Google Forms válido.");
        }
        ValidacaoConfiguracaoMissaoService.Configuracao configuracao = validacaoConfiguracao.validar(dados);
        return new Missao(UUID.randomUUID(), dados.nome().trim(), dados.descricao(), dados.dataInicio(), dados.dataFim(),
                dados.prazoEnvio(), valor(dados.pontosPorPessoa()), valor(dados.pontosPorNovoAfastado()),
                valor(dados.bonificacaoACadaCincoNovos()), valor(dados.metaNacional()),
                valor(dados.bonificacaoMetaNacional()), true,
                dados.tipoPessoa() == null ? "integrante" : dados.tipoPessoa(), link,
                "PUBLICADA", 1, rally, configuracao.regrasPontuacao(), configuracao.camposRelatorio());
    }

    private DadosRally obterDadosRally(RequisicaoCriarMissoes corpo) {
        if (corpo.rally() != null) return corpo.rally();
        LocalDate inicio = corpo.missoes().stream().map(DadosMissao::dataInicio)
                .filter(java.util.Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        LocalDate fim = corpo.missoes().stream().map(DadosMissao::dataFim)
                .filter(java.util.Objects::nonNull).max(LocalDate::compareTo).orElse(null);
        if (inicio == null || fim == null) throw erro("Informe as datas do rally.");
        return new DadosRally("Rally " + inicio + " a " + fim, inicio, fim);
    }

    private void validarCamposRally(DadosRally rally, List<DadosMissao> missoes) {
        validarDadosRally(rally);
        validarDatasMissoesNoPeriodo(rally.dataInicio(), rally.dataFim(), missoes);
    }

    private void validarDadosRally(DadosRally rally) {
        if (rally.nome() == null || rally.nome().isBlank() || rally.nome().length() > 180
                || rally.dataInicio() == null || rally.dataFim() == null
                || rally.dataInicio().isAfter(rally.dataFim())) {
            throw erro("Informe o nome e um período válido para o rally.");
        }
        boolean sobreposto = rallies.findAll().stream().anyMatch(existente ->
                !rally.dataFim().isBefore(existente.getDataInicio())
                        && !rally.dataInicio().isAfter(existente.getDataFim()));
        if (sobreposto) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "O período informado coincide com outro rally cadastrado.");
    }

    private void validarDatasMissoesNoRally(Rally rally, List<DadosMissao> missoes) {
        validarDatasMissoesNoPeriodo(rally.getDataInicio(), rally.getDataFim(), missoes);
    }

    private void validarDatasMissoesNoPeriodo(LocalDate inicioRally, LocalDate fimRally,
                                               List<DadosMissao> missoes) {
        for (DadosMissao missao : missoes) {
            if (missao.dataInicio() == null || missao.dataFim() == null
                    || missao.dataInicio().isBefore(inicioRally) || missao.dataFim().isAfter(fimRally)) {
                throw erro("As datas de cada missão precisam estar dentro do período do rally.");
            }
        }
    }

    private static int valor(Integer valor) { return valor == null ? 0 : valor; }

    private static ResponseStatusException erro(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
