package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.ArquivoDrive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ArquivoDriveRepositorio extends JpaRepository<ArquivoDrive, UUID> {
    List<ArquivoDrive> findAllByRelatorioId(UUID relatorioId);
}
