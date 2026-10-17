package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.Missao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MissaoRepositorio extends JpaRepository<Missao, UUID> {
}
