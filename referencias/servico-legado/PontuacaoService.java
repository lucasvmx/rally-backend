package br.org.fju.rally.servico;

import br.org.fju.rally.api.RespostaPlacar;
import br.org.fju.rally.dominio.Missao;
import br.org.fju.rally.dominio.Relatorio;
import br.org.fju.rally.dominio.Tribo;
import br.org.fju.rally.repositorio.RelatorioRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PontuacaoService {
    private final RelatorioRepositorio relatorioRepositorio;
    private final TriboRepositorio triboRepositorio;

    public PontuacaoService(RelatorioRepositorio relatorioRepositorio, TriboRepositorio triboRepositorio) {
        this.relatorioRepositorio = relatorioRepositorio;
        this.triboRepositorio = triboRepositorio;
    }

    public int calcularPontosBase(Missao missao, int pessoas, int novosAfastados) {
        return pessoas * missao.getPontosPorPessoa() + novosAfastados * missao.getPontosPorNovoAfastado();
    }

    public List<RespostaPlacar> gerarPlacar() {
        List<Tribo> tribos = triboRepositorio.findAll();
        List<Relatorio> aprovados = relatorioRepositorio.findByStatus("APROVADO");
        Map<UUID, Long> pontos = new HashMap<>();
        Map<UUID, Long> pessoas = new HashMap<>();
        Map<UUID, Long> relatorios = new HashMap<>();
        Map<UUID, Long> bonusNacional = new HashMap<>();

        tribos.forEach(tribo -> {
            pontos.put(tribo.getId(), 0L);
            pessoas.put(tribo.getId(), 0L);
            relatorios.put(tribo.getId(), 0L);
            bonusNacional.put(tribo.getId(), 0L);
        });

        Map<String, Integer> novosPorTriboEMissao = new HashMap<>();
        for (Relatorio relatorio : aprovados) {
            UUID triboId = relatorio.getTribo().getId();
            Missao missao = relatorio.getMissao();
            pontos.computeIfPresent(triboId, (id, valor) -> valor + relatorio.getPontosAprovados());
            pessoas.computeIfPresent(triboId, (id, valor) -> valor + relatorio.getQuantidadePessoas());
            relatorios.computeIfPresent(triboId, (id, valor) -> valor + 1);
            String chave = triboId + ":" + missao.getId();
            novosPorTriboEMissao.merge(chave, relatorio.getQuantidadeNovosAfastados(), Integer::sum);
        }

        for (Relatorio relatorio : aprovados) {
            Missao missao = relatorio.getMissao();
            if (missao.getBonificacaoACadaCincoNovos() <= 0) continue;
            String chave = relatorio.getTribo().getId() + ":" + missao.getId();
            int totalNovos = novosPorTriboEMissao.getOrDefault(chave, 0);
            long bonus = (long) (totalNovos / 5) * missao.getBonificacaoACadaCincoNovos();
            UUID triboId = relatorio.getTribo().getId();
            pontos.computeIfPresent(triboId, (id, valor) -> valor + bonus);
        }

        Map<String, Integer> pessoasPorNacaoEMissao = new HashMap<>();
        Map<String, Missao> missoesComMeta = new HashMap<>();
        for (Relatorio relatorio : aprovados) {
            Missao missao = relatorio.getMissao();
            if (missao.getMetaNacional() <= 0) continue;
            String chave = relatorio.getTribo().getNacao().getId() + ":" + missao.getId();
            pessoasPorNacaoEMissao.merge(chave, relatorio.getQuantidadePessoas(), Integer::sum);
            missoesComMeta.put(chave, missao);
        }
        Set<String> metasAplicadas = new HashSet<>();
        for (Tribo tribo : tribos) {
            for (Map.Entry<String, Missao> entrada : missoesComMeta.entrySet()) {
                String[] partes = entrada.getKey().split(":");
                if (!tribo.getNacao().getId().toString().equals(partes[0])) continue;
                Missao missao = entrada.getValue();
                if (pessoasPorNacaoEMissao.getOrDefault(entrada.getKey(), 0) >= missao.getMetaNacional()) {
                    long bonus = missao.getBonificacaoMetaNacional();
                    pontos.computeIfPresent(tribo.getId(), (id, valor) -> valor + bonus);
                    bonusNacional.computeIfPresent(tribo.getId(), (id, valor) -> valor + bonus);
                    metasAplicadas.add(entrada.getKey());
                }
            }
        }

        List<RespostaPlacar> resposta = new ArrayList<>();
        tribos.stream()
                .sorted(Comparator.comparingLong((Tribo tribo) -> pontos.getOrDefault(tribo.getId(), 0L)).reversed()
                        .thenComparing(Tribo::getNome))
                .forEach(tribo -> resposta.add(new RespostaPlacar(
                        resposta.size() + 1,
                        tribo.getId(), tribo.getNome(), tribo.getNacao().getNome(),
                        pontos.getOrDefault(tribo.getId(), 0L),
                        pessoas.getOrDefault(tribo.getId(), 0L),
                        relatorios.getOrDefault(tribo.getId(), 0L),
                        bonusNacional.getOrDefault(tribo.getId(), 0L))));
        return resposta;
    }
}
