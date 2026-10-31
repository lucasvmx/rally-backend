package br.org.fju.rally.repositorio;

import br.org.fju.rally.dominio.Relatorio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RelatorioRepositorio extends JpaRepository<Relatorio, UUID> {
    interface ResumoDashboard {
        String getStatus();
        long getTotal();
        long getPessoas();
        long getNovos();
    }

    @Query("""
            select r.status as status, count(r) as total,
                   coalesce(sum(r.quantidadePessoas), 0) as pessoas,
                   coalesce(sum(r.quantidadeNovosAfastados), 0) as novos
            from Relatorio r join r.missao m join r.tribo t
            where m.rally.id = :rallyId and
                  (:privilegiado = true or t.id = :triboId or t.nacao.id = :nacaoId)
            group by r.status
            """)
    List<ResumoDashboard> resumoDashboard(UUID rallyId, boolean privilegiado, UUID triboId, UUID nacaoId);

    List<Relatorio> findAllByOrderByEnviadoEmDesc();

    List<Relatorio> findByStatus(String status);

    @Query("select r from Relatorio r join fetch r.missao join fetch r.tribo t join fetch t.nacao order by r.enviadoEm desc")
    List<Relatorio> buscarComReferencias();
}
