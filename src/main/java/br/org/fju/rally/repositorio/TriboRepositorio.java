package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.Tribo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TriboRepositorio extends JpaRepository<Tribo, UUID> {
}
