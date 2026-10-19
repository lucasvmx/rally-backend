package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.PessoaPontuada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PessoaPontuadaRepositorio extends JpaRepository<PessoaPontuada, UUID> {
    List<PessoaPontuada> findByRelatorioIdIn(Collection<UUID> relatorioIds);
    List<PessoaPontuada> findByMissaoIdAndNomeNormalizado(UUID missaoId, String nomeNormalizado);
    List<PessoaPontuada> findByRelatorioId(UUID relatorioId);
}
