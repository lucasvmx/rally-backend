package br.org.fju.rally.servico;

import br.org.fju.rally.api.RespostaPlacar;
import br.org.fju.rally.dominio.MetaNacional;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.PessoaPontuada;
import br.org.fju.rally.dominio.PontuacaoManual;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.dominio.Rally;
import br.org.fju.rally.dominio.Tribo;
import br.org.fju.rally.repositorio.MetaNacionalRepositorio;
import br.org.fju.rally.repositorio.PessoaPontuadaRepositorio;
import br.org.fju.rally.repositorio.PontuacaoManualRepositorio;
import br.org.fju.rally.repositorio.RallyRepositorio;
import br.org.fju.rally.repositorio.RelatorioRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

@Service
public class PontuacaoService {
    private final RelatorioRepositorio relatorioRepositorio;
    private final TriboRepositorio triboRepositorio;
    private final MetaNacionalRepositorio metaNacionalRepositorio;
    private final RallyRepositorio rallyRepositorio;
    private final PessoaPontuadaRepositorio pessoaRepositorio;
    private final RegrasPontuacaoService regras;
    private final PontuacaoManualRepositorio pontuacaoManualRepositorio;

    @Autowired
    public PontuacaoService(RelatorioRepositorio relatorioRepositorio,
                            TriboRepositorio triboRepositorio,
                            MetaNacionalRepositorio metaNacionalRepositorio,
                            RallyRepositorio rallyRepositorio,
                            PessoaPontuadaRepositorio pessoaRepositorio,
                            RegrasPontuacaoService regras,
                            PontuacaoManualRepositorio pontuacaoManualRepositorio) {
        this.relatorioRepositorio = relatorioRepositorio;
        this.triboRepositorio = triboRepositorio;
        this.metaNacionalRepositorio = metaNacionalRepositorio;
        this.rallyRepositorio = rallyRepositorio;
        this.pessoaRepositorio = pessoaRepositorio;
        this.regras = regras;
        this.pontuacaoManualRepositorio = pontuacaoManualRepositorio;
    }

    public PontuacaoService(RelatorioRepositorio relatorioRepositorio,
                            TriboRepositorio triboRepositorio,
                            MetaNacionalRepositorio metaNacionalRepositorio,
                            RallyRepositorio rallyRepositorio,
                            PessoaPontuadaRepositorio pessoaRepositorio,
                            RegrasPontuacaoService regras) {
        this(relatorioRepositorio, triboRepositorio, metaNacionalRepositorio, rallyRepositorio,
                pessoaRepositorio, regras, null);
    }

    public PontuacaoService(RelatorioRepositorio relatorioRepositorio,
                            TriboRepositorio triboRepositorio,
                            MetaNacionalRepositorio metaNacionalRepositorio) {
        this(relatorioRepositorio, triboRepositorio, metaNacionalRepositorio, null, null,
                new RegrasPontuacaoService(), null);
    }

    public int calcularPontos(Relatorio relatorio) {
        List<PessoaPontuada> pessoas = pessoaRepositorio == null ? List.of()
                : pessoaRepositorio.findByRelatorioId(relatorio.getId());
        return regras.calcularPontos(relatorio, pessoas);
    }

    @Transactional(readOnly = true)
    public List<RespostaPlacar> gerarPlacar() {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        Rally rallyAtual = rallyRepositorio == null ? null : rallyRepositorio.findAll().stream()
                .filter(rally -> rally.contem(hoje))
                .max(Comparator.comparing(Rally::getDataInicio))
                .orElse(null);
        return gerarPlacar(rallyAtual);
    }

    @Transactional(readOnly = true)
    public List<RespostaPlacar> gerarPlacar(UUID rallyId) {
        if (rallyRepositorio == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rally não encontrado.");
        }
        Rally rally = rallyRepositorio.findById(rallyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rally não encontrado."));
        return gerarPlacar(rally);
    }

    private List<RespostaPlacar> gerarPlacar(Rally rallyAtual) {
        List<Tribo> tribos = triboRepositorio.findAll();

        Map<UUID, Long> pontosPorTribo = new HashMap<>();
        Map<UUID, Long> pessoasPorTribo = new HashMap<>();
        Map<UUID, Long> relatoriosPorTribo = new HashMap<>();
        Map<UUID, Long> bonusPorNacao = new HashMap<>();
        if (rallyAtual != null) {
            List<Relatorio> aprovados = relatorioRepositorio.findByStatus("APROVADO").stream()
                    .filter(relatorio -> relatorio.getMissao().getRally().getId().equals(rallyAtual.getId()))
                    .toList();
            adicionarPontosDosRelatorios(aprovados, pontosPorTribo, pessoasPorTribo, relatoriosPorTribo);
            adicionarPontuacoesManuais(rallyAtual.getId(), pontosPorTribo);
            aplicarBonificacoesNacionaisLegadas(aprovados, bonusPorNacao);
            aplicarBonificacoesDinamicas(aprovados, tribos, pontosPorTribo, bonusPorNacao);
        }

        List<PlacarInterno> placares = new ArrayList<>();
        for (Tribo tribo : tribos) {
            long bonusNacional = bonusPorNacao.getOrDefault(tribo.getNacao().getId(), 0L);
            long pontos = pontosPorTribo.getOrDefault(tribo.getId(), 0L) + bonusNacional;
            placares.add(new PlacarInterno(tribo, pontos,
                    pessoasPorTribo.getOrDefault(tribo.getId(), 0L),
                    relatoriosPorTribo.getOrDefault(tribo.getId(), 0L), bonusNacional));
        }

        placares.sort(Comparator.comparingLong(PlacarInterno::pontos).reversed()
                .thenComparing(item -> item.tribo().getNome()));
        List<RespostaPlacar> resposta = new ArrayList<>();
        for (int indice = 0; indice < placares.size(); indice++) {
            PlacarInterno placar = placares.get(indice);
            resposta.add(new RespostaPlacar(indice + 1, placar.tribo().getId(), placar.tribo().getNome(),
                    placar.tribo().getNacao().getNome(), placar.pontos(), placar.pessoasAprovadas(),
                    placar.relatoriosAprovados(), placar.bonusNacional()));
        }
        return resposta;
    }

    private void adicionarPontuacoesManuais(UUID rallyId, Map<UUID, Long> pontosPorTribo) {
        if (pontuacaoManualRepositorio == null) return;
        for (PontuacaoManual pontuacao : pontuacaoManualRepositorio.findAllByRallyId(rallyId)) {
            for (Tribo tribo : pontuacao.getTribosDestinatarias()) {
                pontosPorTribo.merge(tribo.getId(), (long) pontuacao.getPontosPorTribo(), Long::sum);
            }
        }
    }

    private void adicionarPontosDosRelatorios(List<Relatorio> aprovados,
                                               Map<UUID, Long> pontosPorTribo,
                                               Map<UUID, Long> pessoasPorTribo,
                                               Map<UUID, Long> relatoriosPorTribo) {
        if (aprovados.isEmpty()) return;
        Collection<UUID> ids = aprovados.stream().map(Relatorio::getId).toList();
        Map<UUID, List<PessoaPontuada>> pessoasPorRelatorio = pessoaRepositorio == null ? Map.of()
                : pessoaRepositorio.findByRelatorioIdIn(ids).stream()
                    .collect(Collectors.groupingBy(pessoa -> pessoa.getRelatorio().getId()));
        for (Relatorio relatorio : aprovados) {
            UUID triboId = relatorio.getTribo().getId();
            long pontos = relatorio.getPontosAprovados();
            if (pontos == 0) pontos = regras.calcularPontos(relatorio,
                    pessoasPorRelatorio.getOrDefault(relatorio.getId(), List.of()));
            pontosPorTribo.merge(triboId, pontos, Long::sum);
            pessoasPorTribo.merge(triboId, (long) relatorio.getQuantidadePessoas()
                    + relatorio.getQuantidadeNovosAfastados(), Long::sum);
            relatoriosPorTribo.merge(triboId, 1L, Long::sum);
        }
    }

    private void aplicarBonificacoesNacionaisLegadas(List<Relatorio> aprovados, Map<UUID, Long> bonusPorNacao) {
        Map<ChaveMeta, Long> quantidadePorMeta = new HashMap<>();
        for (Relatorio relatorio : aprovados) {
            if (relatorio.getMissao().getRegrasPontuacao() != null
                    && !relatorio.getMissao().getRegrasPontuacao().isEmpty()) continue;
            UUID nacaoId = relatorio.getTribo().getNacao().getId();
            UUID missaoId = relatorio.getMissao().getId();
            quantidadePorMeta.merge(new ChaveMeta(missaoId, nacaoId),
                    (long) quantidadeConsiderada(relatorio), Long::sum);
        }
        if (metaNacionalRepositorio == null) return;
        for (MetaNacional meta : metaNacionalRepositorio.findAll()) {
            long quantidade = quantidadePorMeta.getOrDefault(
                    new ChaveMeta(meta.getMissao().getId(), meta.getNacao().getId()), 0L);
            if (quantidade >= meta.getQuantidadeNecessaria()) {
                bonusPorNacao.merge(meta.getNacao().getId(), (long) meta.getPremioEmTalentos(), Long::sum);
            }
        }
    }

    private void aplicarBonificacoesDinamicas(List<Relatorio> aprovados, List<Tribo> tribos,
                                               Map<UUID, Long> pontosPorTribo,
                                               Map<UUID, Long> bonusPorNacao) {
        if (aprovados.isEmpty()) return;
        Collection<UUID> ids = aprovados.stream().map(Relatorio::getId).toList();
        Map<UUID, List<PessoaPontuada>> pessoasPorRelatorio = pessoaRepositorio == null ? Map.of()
                : pessoaRepositorio.findByRelatorioIdIn(ids).stream()
                    .collect(Collectors.groupingBy(pessoa -> pessoa.getRelatorio().getId()));
        Map<UUID, List<Relatorio>> porMissao = aprovados.stream()
                .collect(Collectors.groupingBy(relatorio -> relatorio.getMissao().getId()));

        for (List<Relatorio> relatoriosMissao : porMissao.values()) {
            Missao missao = relatoriosMissao.getFirst().getMissao();
            List<Map<String, Object>> regrasMissao = missao.getRegrasPontuacao();
            if (regrasMissao == null) continue;
            for (Map<String, Object> regra : regrasMissao) {
                String tipo = String.valueOf(regra.get("tipo"));
                boolean percentual = List.of("PERCENTUAL", "FAIXAS_PERCENTUAIS").contains(tipo);
                if (!percentual && !List.of("A_CADA", "META", "FAIXAS").contains(tipo)) continue;
                String escopo = regras.escopo(regra);
                Map<UUID, BigDecimal> somas = new HashMap<>();
                Map<UUID, BigDecimal> basesPercentuais = new HashMap<>();
                Map<UUID, Set<UUID>> tribosGeradoras = new HashMap<>();
                for (Relatorio relatorio : relatoriosMissao) {
                    if (!regras.condicaoAtendida(regra.get("quando"), relatorio.getDados(), Map.of())) continue;
                    UUID agrupador = "NACAO".equals(escopo)
                            ? relatorio.getTribo().getNacao().getId() : relatorio.getTribo().getId();
                    tribosGeradoras.computeIfAbsent(agrupador, ignorado -> new HashSet<>())
                            .add(relatorio.getTribo().getId());
                    BigDecimal quantidade = regras.metric(relatorio,
                            pessoasPorRelatorio.getOrDefault(relatorio.getId(), List.of()),
                            String.valueOf(regra.get("campo")), regra.get("quando_pessoa"));
                    somas.merge(agrupador, quantidade, BigDecimal::add);
                    if (percentual) {
                        BigDecimal base = regras.metric(relatorio,
                                pessoasPorRelatorio.getOrDefault(relatorio.getId(), List.of()),
                                String.valueOf(regra.get("campo_base")));
                        basesPercentuais.merge(agrupador, base, BigDecimal::add);
                    }
                }
                for (Map.Entry<UUID, BigDecimal> soma : somas.entrySet()) {
                    long pontos = percentual
                            ? regras.calcularBonusPercentual(regra, soma.getValue(),
                                    basesPercentuais.getOrDefault(soma.getKey(), BigDecimal.ZERO))
                            : regras.calcularBonusAgregado(regra, soma.getValue());
                    if (pontos <= 0) continue;
                    if ("NACAO".equals(escopo)) {
                        if ("TRIBO_GERADORA".equalsIgnoreCase(
                                String.valueOf(regra.getOrDefault("distribuir_para", "TRIBOS")))) {
                            tribosGeradoras.getOrDefault(soma.getKey(), Set.of()).forEach(triboId ->
                                    pontosPorTribo.merge(triboId, pontos, Long::sum));
                        } else {
                            bonusPorNacao.merge(soma.getKey(), pontos, Long::sum);
                        }
                    } else {
                        pontosPorTribo.merge(soma.getKey(), pontos, Long::sum);
                    }
                }
            }
        }
    }

    private int quantidadeConsiderada(Relatorio relatorio) {
        ToIntFunction<Relatorio> seletor = "novo_ou_afastado".equalsIgnoreCase(relatorio.getMissao().getTipoPessoa())
                ? Relatorio::getQuantidadeNovosAfastados : Relatorio::getQuantidadePessoas;
        return seletor.applyAsInt(relatorio);
    }

    private record ChaveMeta(UUID missaoId, UUID nacaoId) {}

    private record PlacarInterno(Tribo tribo, long pontos, long pessoasAprovadas,
                                 long relatoriosAprovados, long bonusNacional) {}
}
