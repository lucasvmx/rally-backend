package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.Rally;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RallyRepositorio extends JpaRepository<Rally, UUID> {
}
