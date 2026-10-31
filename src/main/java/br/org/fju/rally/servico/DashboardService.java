package br.org.fju.rally.servico;

import br.org.fju.rally.api.RespostaDashboard;
import br.org.fju.rally.api.RespostaRally;
import br.org.fju.rally.dominio.Rally;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.RallyRepositorio;
import br.org.fju.rally.repositorio.RelatorioRepositorio;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {
    private final RallyRepositorio rallies;
    private final MissaoRepositorio missoes;
    private final RelatorioRepositorio relatorios;
    private final PontuacaoService pontuacao;
    private final AcessoService acesso;

    public DashboardService(RallyRepositorio rallies, MissaoRepositorio missoes,
                            RelatorioRepositorio relatorios, PontuacaoService pontuacao, AcessoService acesso) {
        this.rallies = rallies;
        this.missoes = missoes;
        this.relatorios = relatorios;
        this.pontuacao = pontuacao;
        this.acesso = acesso;
    }

    @Transactional(readOnly = true)
    public RespostaDashboard consultar(UUID rallyId, HttpServletRequest requisicao) {
        var contexto = acesso.contexto(requisicao);
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Rally rally = rallyId == null ? rallies.findAll().stream().filter(item -> item.contem(hoje))
                .max(Comparator.comparing(Rally::getDataInicio)).orElse(null)
                : rallies.findById(rallyId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Rally não encontrado."));
        if (rally == null) {
            return new RespostaDashboard(null, new RespostaDashboard.Missoes(0),
                    new RespostaDashboard.Relatorios(0, 0, 0, 0, 0, 0), List.of());
        }
        long enviados = 0, aprovados = 0, rejeitados = 0, total = 0, pessoas = 0, novos = 0;
        for (var grupo : relatorios.resumoDashboard(rally.getId(), contexto.privilegiado(),
                contexto.triboId(), contexto.nacaoId())) {
            total += grupo.getTotal();
            switch (grupo.getStatus()) {
                case "ENVIADO" -> enviados = grupo.getTotal();
                case "APROVADO" -> {
                    aprovados = grupo.getTotal();
                    pessoas = grupo.getPessoas();
                    novos = grupo.getNovos();
                }
                case "REJEITADO" -> rejeitados = grupo.getTotal();
                default -> { }
            }
        }
        return new RespostaDashboard(RespostaRally.de(rally, hoje),
                new RespostaDashboard.Missoes(missoes.countByRallyId(rally.getId())),
                new RespostaDashboard.Relatorios(total, enviados, aprovados, rejeitados, pessoas, novos),
                pontuacao.gerarPlacar(rally.getId()).stream().limit(5).toList());
    }
}
