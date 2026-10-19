package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.Tribo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TriboRepositorio extends JpaRepository<Tribo, UUID> {
    @Query("select tribo from Tribo tribo join fetch tribo.nacao")
    List<Tribo> buscarTodasComNacao();
}
