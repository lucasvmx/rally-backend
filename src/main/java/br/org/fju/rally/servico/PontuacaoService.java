package br.org.fju.rally.servico;

import br.org.fju.rally.api.RespostaPlacar;
import br.org.fju.rally.dominio.MetaNacional;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.dominio.Tribo;
import br.org.fju.rally.repositorio.MetaNacionalRepositorio;
import br.org.fju.rally.repositorio.RelatorioRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToIntFunction;

@Service
public class PontuacaoService {
    private final RelatorioRepositorio relatorioRepositorio;
    private final TriboRepositorio triboRepositorio;
    private final MetaNacionalRepositorio metaNacionalRepositorio;

    public PontuacaoService(RelatorioRepositorio relatorioRepositorio,
                            TriboRepositorio triboRepositorio,
                            MetaNacionalRepositorio metaNacionalRepositorio) {
        this.relatorioRepositorio = relatorioRepositorio;
        this.triboRepositorio = triboRepositorio;
        this.metaNacionalRepositorio = metaNacionalRepositorio;
    }

    public int calcularPontos(Relatorio relatorio) {
        Missao missao = relatorio.getMissao();
        int pontos = relatorio.getQuantidadePessoas() * missao.getPontosPorPessoa();
        pontos += relatorio.getQuantidadeNovosAfastados() * missao.getPontosPorNovoAfastado();

        if (missao.getBonificacaoACadaCincoNovos() > 0) {
            pontos += (relatorio.getQuantidadeNovosAfastados() / 5) * missao.getBonificacaoACadaCincoNovos();
        }
        return pontos;
    }

    @Transactional(readOnly = true)
    public List<RespostaPlacar> gerarPlacar() {
        List<Relatorio> aprovados = relatorioRepositorio.findByStatus("APROVADO");
        List<Tribo> tribos = triboRepositorio.findAll();
        Map<UUID, Integer> pontosPorTribo = new HashMap<>();
        Map<UUID, Long> pessoasPorTribo = new HashMap<>();
        Map<UUID, Long> relatoriosPorTribo = new HashMap<>();

        for (Relatorio relatorio : aprovados) {
            UUID triboId = relatorio.getTribo().getId();
            pontosPorTribo.merge(triboId, calcularPontos(relatorio), Integer::sum);
            pessoasPorTribo.merge(triboId, (long) relatorio.getQuantidadePessoas(), Long::sum);
            relatoriosPorTribo.merge(triboId, 1L, Long::sum);
        }

        Map<ChaveMeta, Long> quantidadePorMeta = new HashMap<>();
        for (Relatorio relatorio : aprovados) {
            UUID nacaoId = relatorio.getTribo().getNacao().getId();
            UUID missaoId = relatorio.getMissao().getId();
            quantidadePorMeta.merge(new ChaveMeta(missaoId, nacaoId),
                    (long) quantidadeConsiderada(relatorio), Long::sum);
        }

        Map<UUID, Long> bonusPorNacao = new HashMap<>();
        for (MetaNacional meta : metaNacionalRepositorio.findAll()) {
            long quantidade = quantidadePorMeta.getOrDefault(
                    new ChaveMeta(meta.getMissao().getId(), meta.getNacao().getId()), 0L);
            if (quantidade >= meta.getQuantidadeNecessaria()) {
                bonusPorNacao.merge(meta.getNacao().getId(), (long) meta.getPremioEmTalentos(), Long::sum);
            }
        }

        List<PlacarInterno> placares = new ArrayList<>();
        for (Tribo tribo : tribos) {
            long bonusNacional = bonusPorNacao.getOrDefault(tribo.getNacao().getId(), 0L);
            long pontos = pontosPorTribo.getOrDefault(tribo.getId(), 0) + bonusNacional;
            placares.add(new PlacarInterno(
                    tribo,
                    pontos,
                    pessoasPorTribo.getOrDefault(tribo.getId(), 0L),
                    relatoriosPorTribo.getOrDefault(tribo.getId(), 0L),
                    bonusNacional));
        }

        placares.sort(Comparator.comparingLong(PlacarInterno::pontos).reversed()
                .thenComparing(item -> item.tribo().getNome()));

        List<RespostaPlacar> resposta = new ArrayList<>();
        for (int indice = 0; indice < placares.size(); indice++) {
            PlacarInterno placar = placares.get(indice);
            resposta.add(new RespostaPlacar(
                    indice + 1,
                    placar.tribo().getId(),
                    placar.tribo().getNome(),
                    placar.tribo().getNacao().getNome(),
                    placar.pontos(),
                    placar.pessoasAprovadas(),
                    placar.relatoriosAprovados(),
                    placar.bonusNacional()));
        }
        return resposta;
    }

    private int quantidadeConsiderada(Relatorio relatorio) {
        ToIntFunction<Relatorio> seletor = "novo_ou_afastado".equalsIgnoreCase(relatorio.getMissao().getTipoPessoa())
                ? Relatorio::getQuantidadeNovosAfastados
                : Relatorio::getQuantidadePessoas;
        return seletor.applyAsInt(relatorio);
    }

    private record ChaveMeta(UUID missaoId, UUID nacaoId) {}

    private record PlacarInterno(Tribo tribo, long pontos, long pessoasAprovadas,
                                 long relatoriosAprovados, long bonusNacional) {}
}
