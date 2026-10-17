package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.MetaNacional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MetaNacionalRepositorio extends JpaRepository<MetaNacional, UUID> {
    List<MetaNacional> findAllByMissaoId(UUID missaoId);
}
