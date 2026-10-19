package br.org.fju.rally.servico;

import br.org.fju.rally.api.DadosMissao;
import br.org.fju.rally.api.RequisicaoCriarMissoes;
import br.org.fju.rally.api.RequisicaoInterpretacao;
import br.org.fju.rally.api.RespostaInterpretacao;
import br.org.fju.rally.api.RespostaInterpretacaoMissoes;
import br.org.fju.rally.api.RespostaMissao;
import br.org.fju.rally.dominio.MetaNacional;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.Nacao;
import br.org.fju.rally.repositorio.MetaNacionalRepositorio;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.NacaoRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class MissaoService {
    private final MissaoRepositorio missoes;
    private final MetaNacionalRepositorio metasNacionais;
    private final NacaoRepositorio nacoes;
    private final InterpretadorDeRegras interpretador;
    private final AcessoService acesso;

    public MissaoService(MissaoRepositorio missoes, MetaNacionalRepositorio metasNacionais,
                         NacaoRepositorio nacoes, InterpretadorDeRegras interpretador, AcessoService acesso) {
        this.missoes = missoes;
        this.metasNacionais = metasNacionais;
        this.nacoes = nacoes;
        this.interpretador = interpretador;
        this.acesso = acesso;
    }

    public RespostaInterpretacaoMissoes interpretar(RequisicaoInterpretacao corpo, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        return interpretador.interpretarMissoes(corpo.texto());
    }

    public RespostaInterpretacao interpretarRegras(RequisicaoInterpretacao corpo, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        return interpretador.interpretar(corpo.texto());
    }

    @Transactional
    public List<RespostaMissao> criar(RequisicaoCriarMissoes corpo, HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        List<Missao> novas = corpo.missoes().stream().map(this::converter).toList();
        List<Missao> salvas = missoes.saveAllAndFlush(novas);
        List<Nacao> nacoesDisponiveis = corpo.missoes().stream()
                .anyMatch(dados -> dados.metaNacional() > 0 && dados.bonificacaoMetaNacional() > 0)
                ? nacoes.findAll() : List.of();
        for (int indice = 0; indice < salvas.size(); indice++) {
            DadosMissao dados = corpo.missoes().get(indice);
            Missao missao = salvas.get(indice);
            if (dados.metaNacional() > 0 && dados.bonificacaoMetaNacional() > 0) {
                metasNacionais.saveAll(nacoesDisponiveis.stream().map(nacao -> new MetaNacional(
                        UUID.randomUUID(), missao, nacao, dados.metaNacional(),
                        dados.bonificacaoMetaNacional())).toList());
            }
        }
        return salvas.stream().map(RespostaMissao::de).toList();
    }

    private Missao converter(DadosMissao dados) {
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
        return new Missao(UUID.randomUUID(), dados.nome().trim(), dados.descricao(), dados.dataInicio(), dados.dataFim(),
                dados.prazoEnvio(), dados.pontosPorPessoa(), dados.pontosPorNovoAfastado(),
                dados.bonificacaoACadaCincoNovos(), dados.metaNacional(), dados.bonificacaoMetaNacional(),
                dados.exigeFoto(), dados.tipoPessoa(), link, "PUBLICADA", 1);
    }

    private static ResponseStatusException erro(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
