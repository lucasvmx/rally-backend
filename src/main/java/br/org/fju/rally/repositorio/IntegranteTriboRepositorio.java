package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.IntegranteTribo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegranteTriboRepositorio extends JpaRepository<IntegranteTribo, UUID> {
    List<IntegranteTribo> findByTriboIdOrderByNomeAsc(UUID triboId);
    Optional<IntegranteTribo> findByIdAndTriboId(UUID id, UUID triboId);
    long countByTriboId(UUID triboId);
}
