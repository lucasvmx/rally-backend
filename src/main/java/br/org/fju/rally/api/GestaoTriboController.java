package br.org.fju.rally.api;

import br.org.fju.rally.dominio.IntegranteTribo;
import br.org.fju.rally.dominio.Tribo;
import br.org.fju.rally.repositorio.IntegranteTriboRepositorio;
import br.org.fju.rally.repositorio.MissaoRepositorio;
import br.org.fju.rally.repositorio.RelatorioRepositorio;
import br.org.fju.rally.repositorio.TriboRepositorio;
import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.PapelUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tribos/{triboId}")
public class GestaoTriboController {
    private final TriboRepositorio tribos;
    private final IntegranteTriboRepositorio integrantes;
    private final RelatorioRepositorio relatorios;
    private final MissaoRepositorio missoes;
    private final AcessoService acesso;
    public GestaoTriboController(TriboRepositorio tribos, IntegranteTriboRepositorio integrantes,
                                  RelatorioRepositorio relatorios, MissaoRepositorio missoes, AcessoService acesso) {
        this.tribos = tribos; this.integrantes = integrantes; this.relatorios = relatorios;
        this.missoes = missoes; this.acesso = acesso;
    }
    public record DadosIntegrante(@NotBlank @Size(max = 160) String nome,
                                  @NotNull IntegranteTribo.Titulo titulo, IntegranteTribo.Papel papel) {}
    public record RespostaIntegrante(UUID id, String nome, IntegranteTribo.Titulo titulo, IntegranteTribo.Papel papel) {
        static RespostaIntegrante de(IntegranteTribo i) { return new RespostaIntegrante(i.getId(), i.getNome(), i.getTitulo(), i.getPapel()); }
    }
    public record Informacoes(long integrantes, long jovensNovosAfastados, long missoesPeriodo,
                              long missoesComRelatorio, Double taxaCumprimento) {}

    private Tribo autorizar(UUID id, HttpServletRequest requisicao) {
        var contexto = acesso.contexto(requisicao);
        Tribo tribo = tribos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tribo não encontrada."));
        if (!contexto.podeAcessarTribo(tribo.getId(), tribo.getNacao().getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tribo fora do escopo da conta.");
        return tribo;
    }
    private IntegranteTribo buscar(UUID triboId, UUID id) {
        return integrantes.findByIdAndTriboId(id, triboId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integrante não encontrado."));
    }
    @GetMapping("/integrantes") @Transactional(readOnly = true)
    public List<RespostaIntegrante> listar(@PathVariable UUID triboId, HttpServletRequest req) {
        autorizar(triboId, req); return integrantes.findByTriboIdOrderByNomeAsc(triboId).stream().map(RespostaIntegrante::de).toList();
    }
    @PostMapping("/integrantes") @ResponseStatus(HttpStatus.CREATED) @Transactional
    public RespostaIntegrante criar(@PathVariable UUID triboId, @Valid @RequestBody DadosIntegrante corpo, HttpServletRequest req) {
        Tribo tribo = autorizar(triboId, req);
        return RespostaIntegrante.de(integrantes.save(new IntegranteTribo(UUID.randomUUID(), tribo, corpo.nome(), corpo.titulo(), corpo.papel())));
    }
    @PutMapping("/integrantes/{id}") @Transactional
    public RespostaIntegrante atualizar(@PathVariable UUID triboId, @PathVariable UUID id, @Valid @RequestBody DadosIntegrante corpo, HttpServletRequest req) {
        autorizar(triboId, req); var integrante = buscar(triboId, id); integrante.atualizar(corpo.nome(), corpo.titulo(), corpo.papel());
        return RespostaIntegrante.de(integrante);
    }
    @DeleteMapping("/integrantes/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void excluir(@PathVariable UUID triboId, @PathVariable UUID id, HttpServletRequest req) {
        autorizar(triboId, req); integrantes.delete(buscar(triboId, id));
    }
    @GetMapping("/relatorios") @Transactional(readOnly = true)
    public List<RespostaRelatorio> relatorios(@PathVariable UUID triboId, HttpServletRequest req) {
        autorizar(triboId, req); var contexto = acesso.contexto(req);
        var resultado = contexto.papel() == PapelUsuario.COORDENADOR_TRIBO
            ? (contexto.usuarioId() == null ? List.<br.org.fju.rally.dominio.Relatorio>of() : relatorios.buscarPorTriboEAutor(triboId, contexto.usuarioId()))
            : relatorios.buscarPorTribo(triboId);
        return resultado.stream().map(RespostaRelatorio::de).toList();
    }
    @GetMapping("/informacoes") @Transactional(readOnly = true)
    public Informacoes informacoes(@PathVariable UUID triboId, HttpServletRequest req) {
        autorizar(triboId, req);
        var zona = ZoneId.of("America/Sao_Paulo"); var hoje = LocalDate.now(zona); var inicio = hoje.withDayOfMonth(1); var fim = inicio.plusMonths(1);
        long jovens = relatorios.somarNovosNoPeriodo(triboId, inicio.atStartOfDay(zona).toInstant(), fim.atStartOfDay(zona).toInstant());
        long total = missoes.countByDataInicioBetween(inicio, hoje);
        long concluidas = relatorios.contarMissoesRelatadas(triboId, inicio, hoje);
        return new Informacoes(integrantes.countByTriboId(triboId), jovens, total, concluidas,
            total == 0 ? null : 100.0 * concluidas / total);
    }
}
