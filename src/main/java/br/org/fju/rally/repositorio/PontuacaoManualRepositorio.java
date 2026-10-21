package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.PontuacaoManual;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PontuacaoManualRepositorio extends JpaRepository<PontuacaoManual, UUID> {
    @EntityGraph(attributePaths = {"rally", "nacao", "triboChamada", "tribosDestinatarias"})
    List<PontuacaoManual> findAllByRallyIdOrderByRegistradaEmDesc(UUID rallyId);

    @EntityGraph(attributePaths = "tribosDestinatarias")
    List<PontuacaoManual> findAllByRallyId(UUID rallyId);
}
