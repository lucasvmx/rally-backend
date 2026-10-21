package br.org.fju.rally.api;

import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.ContaAcessoService;
import br.org.fju.rally.servico.PapelUsuario;
import br.org.fju.rally.servico.UsuarioAcesso;
import br.org.fju.rally.servico.UsuarioAcessoService;
import br.org.fju.rally.servico.ProvisionamentoUsuarioService;
import br.org.fju.rally.servico.TipoContaUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuariosController {
    private final AcessoService acesso;
    private final UsuarioAcessoService usuarios;
    private final ProvisionamentoUsuarioService provisionamento;
    private final ContaAcessoService contas;

    public UsuariosController(AcessoService acesso, UsuarioAcessoService usuarios,
            ProvisionamentoUsuarioService provisionamento, ContaAcessoService contas) {
        this.acesso = acesso;
        this.usuarios = usuarios;
        this.provisionamento = provisionamento;
        this.contas = contas;
    }

    public record RequisicaoPapel(@NotNull PapelUsuario role, UUID tribeId, UUID nationId) {}
    public record RequisicaoCriacao(
            @NotBlank @Email @Size(max = 255) String email,
            @NotNull PapelUsuario role,
            UUID tribeId,
            UUID nationId,
            @NotNull TipoContaUsuario accountType,
            @Size(max = 128) String initialPassword,
            Boolean gerarSenhaProvisoria) {}
    public record RequisicaoAtualizacao(@NotNull PapelUsuario role, UUID tribeId, UUID nationId) {}
    public record RespostaSenhaProvisoria(String senhaProvisoria) {}
    public record RespostaUsuario(UUID id, String email, PapelUsuario role, UUID tribeId, UUID nationId) {
        static RespostaUsuario de(UsuarioAcesso usuario) {
            return new RespostaUsuario(usuario.id(), usuario.email(), usuario.papel(),
                    usuario.triboId(), usuario.nacaoId());
        }
    }
    public record RespostaProvisionamento(UUID id, String email, PapelUsuario role, UUID tribeId,
            UUID nationId, TipoContaUsuario accountType, String senhaProvisoria) {
        static RespostaProvisionamento de(UsuarioAcesso usuario, String senhaProvisoria) {
            return new RespostaProvisionamento(usuario.id(), usuario.email(), usuario.papel(),
                    usuario.triboId(), usuario.nacaoId(), usuario.tipoConta(), senhaProvisoria);
        }
    }

    public record RespostaConta(UUID id, String email, PapelUsuario role, UUID tribeId, UUID nationId,
            TipoContaUsuario accountType, java.time.OffsetDateTime criadaEm,
            java.time.OffsetDateTime ultimoLoginEm) {
        static RespostaConta de(UsuarioAcessoService.ContaGerenciada conta) {
            return new RespostaConta(conta.id(), conta.email(), conta.role(), conta.tribeId(),
                    conta.nationId(), conta.accountType(), conta.criadaEm(), conta.ultimoLoginEm());
        }
    }

    @GetMapping
    public java.util.List<RespostaConta> listar(HttpServletRequest requisicao) {
        acesso.exigirAssistente(requisicao);
        return usuarios.listarContas().stream().map(RespostaConta::de).toList();
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public RespostaProvisionamento criar(@Valid @RequestBody RequisicaoCriacao corpo,
            HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso ator = acesso.contexto(requisicao);
        if (!ator.assistente() && !ator.administradorSistema()) acesso.exigirAssistente(requisicao);
        ProvisionamentoUsuarioService.ResultadoCriacao criado = provisionamento.criar(
                corpo.email(), corpo.role(), corpo.tribeId(), corpo.nationId(), corpo.accountType(),
                corpo.initialPassword(), Boolean.TRUE.equals(corpo.gerarSenhaProvisoria()),
                ator.administradorSistema());
        return RespostaProvisionamento.de(criado.usuario(), criado.senhaProvisoria());
    }

    @PutMapping("/{id}")
    public RespostaConta atualizar(@PathVariable UUID id, @Valid @RequestBody RequisicaoAtualizacao corpo,
            HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso ator = acesso.contexto(requisicao);
        if (!ator.assistente() && !ator.administradorSistema()) acesso.exigirAssistente(requisicao);
        UsuarioAcesso atualizado = usuarios.atualizar(id, corpo.role(), corpo.tribeId(), corpo.nationId(),
                ator.administradorSistema());
        UsuarioAcessoService.ContaGerenciada detalhada = usuarios.buscarContaGerenciadaPorId(atualizado.id());
        if (detalhada == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada.");
        return RespostaConta.de(detalhada);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable UUID id, HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso ator = acesso.contexto(requisicao);
        if (!ator.assistente() && !ator.administradorSistema()) acesso.exigirAssistente(requisicao);
        usuarios.excluir(id, ator.usuarioId(), ator.administradorSistema());
    }

    @PostMapping("/{id}/senha-provisoria")
    public RespostaSenhaProvisoria gerarSenhaProvisoria(@PathVariable UUID id, HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso ator = acesso.contexto(requisicao);
        if (!ator.assistente() && !ator.administradorSistema()) acesso.exigirAssistente(requisicao);
        return new RespostaSenhaProvisoria(contas.gerarSenhaProvisoria(id, ator.administradorSistema()));
    }

    @PutMapping("/{email}/role")
    public RespostaUsuario atribuirPapel(@PathVariable String email,
            @Valid @RequestBody RequisicaoPapel corpo, HttpServletRequest requisicao) {
        acesso.exigirAdministradorSistema(requisicao);
        return RespostaUsuario.de(usuarios.atribuirPapel(email, corpo.role(), corpo.tribeId(), corpo.nationId()));
    }
}
