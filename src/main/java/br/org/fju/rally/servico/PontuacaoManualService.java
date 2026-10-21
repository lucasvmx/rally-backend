package br.org.fju.rally.servico;

import br.org.fju.rally.api.RequisicaoPontuacaoManual;
import br.org.fju.rally.api.RespostaPontuacaoManual;
import br.org.fju.rally.dominio.PontuacaoManual;
import br.org.fju.rally.dominio.Rally;
import br.org.fju.rally.dominio.Tribo;
import br.org.fju.rally.repositorio.PontuacaoManualRepositorio;
import br.org.fju.rally.repositorio.RallyRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class PontuacaoManualService {
    private static final int PONTOS_PADRAO = 7000;
    private static final ZoneId FUSO_RALLY = ZoneId.of("America/Sao_Paulo");

    private final PontuacaoManualRepositorio pontuacoes;
    private final RallyRepositorio rallies;
    private final TriboRepositorio tribos;
    private final AcessoService acesso;

    public PontuacaoManualService(PontuacaoManualRepositorio pontuacoes,
                                  RallyRepositorio rallies,
                                  TriboRepositorio tribos,
                                  AcessoService acesso) {
        this.pontuacoes = pontuacoes;
        this.rallies = rallies;
        this.tribos = tribos;
        this.acesso = acesso;
    }

    @Transactional(readOnly = true)
    public List<RespostaPontuacaoManual> listar(HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        Rally rally = rallyAtual();
        if (rally == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Não há um Rally ativo para consultar ou registrar bonificações.");
        }
        return pontuacoes.findAllByRallyIdOrderByRegistradaEmDesc(rally.getId()).stream()
                .map(RespostaPontuacaoManual::de)
                .toList();
    }

    @Transactional
    public RespostaPontuacaoManual registrar(RequisicaoPontuacaoManual dados,
                                             HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        Rally rally = rallyAtual();
        if (rally == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Não há um Rally ativo para registrar esta bonificação.");
        }

        Tribo triboChamada = tribos.findById(dados.triboChamadaId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tribo chamada não encontrada."));
        if (!triboChamada.getNacao().getId().equals(dados.nacaoId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A tribo chamada precisa pertencer à nação selecionada.");
        }

        List<Tribo> destinatarias = tribos.buscarTodasComNacao().stream()
                .filter(tribo -> tribo.getNacao().getId().equals(dados.nacaoId()))
                .toList();
        if (destinatarias.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A nação selecionada não possui tribos cadastradas.");
        }

        int pontos = dados.pontosPorTribo() == null ? PONTOS_PADRAO : dados.pontosPorTribo();
        String responsavel = requisicao.getUserPrincipal() instanceof Authentication autenticacao
                ? autenticacao.getName() : "usuario autenticado";
        if (responsavel == null || responsavel.isBlank()) responsavel = "usuario autenticado";

        PontuacaoManual registro = new PontuacaoManual(
                UUID.randomUUID(), rally, triboChamada.getNacao(), triboChamada,
                dados.atividade().trim(), pontos, responsavel,
                LocalDateTime.now(FUSO_RALLY), destinatarias);
        return RespostaPontuacaoManual.de(pontuacoes.save(registro));
    }

    private Rally rallyAtual() {
        LocalDate hoje = LocalDate.now(FUSO_RALLY);
        return rallies.findAll().stream()
                .filter(rally -> rally.contem(hoje))
                .max(java.util.Comparator.comparing(Rally::getDataInicio))
                .orElse(null);
    }
}
