package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.Relatorio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RelatorioRepositorio extends JpaRepository<Relatorio, UUID> {
    List<Relatorio> findAllByOrderByEnviadoEmDesc();

    List<Relatorio> findByStatus(String status);

    @Query("select r from Relatorio r join fetch r.missao join fetch r.tribo t join fetch t.nacao order by r.enviadoEm desc")
    List<Relatorio> buscarComReferencias();
}
