package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.Missao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;

public interface MissaoRepositorio extends JpaRepository<Missao, UUID> {
    long countByRallyId(UUID rallyId);

    @Query("select m from Missao m left join fetch m.rally")
    List<Missao> buscarTodasComRally();

    @Query("select m from Missao m left join fetch m.rally where m.id = :id")
    java.util.Optional<Missao> buscarPorIdComRally(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Missao m where m.id = :id")
    java.util.Optional<Missao> buscarParaEnvio(@Param("id") UUID id);
}
