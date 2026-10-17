package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.Nacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NacaoRepositorio extends JpaRepository<Nacao, UUID> {
}
