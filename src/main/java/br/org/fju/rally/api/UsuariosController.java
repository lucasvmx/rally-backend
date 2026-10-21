package br.org.fju.rally.api;

import br.org.fju.rally.servico.AcessoService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuariosController {
    private final AcessoService acesso;
    private final UsuarioAcessoService usuarios;
    private final ProvisionamentoUsuarioService provisionamento;

    public UsuariosController(AcessoService acesso, UsuarioAcessoService usuarios,
            ProvisionamentoUsuarioService provisionamento) {
        this.acesso = acesso;
        this.usuarios = usuarios;
        this.provisionamento = provisionamento;
    }

    public record RequisicaoPapel(@NotNull PapelUsuario role, UUID tribeId, UUID nationId) {}
    public record RequisicaoCriacao(
            @NotBlank @Email @Size(max = 255) String email,
            @NotNull PapelUsuario role,
            UUID tribeId,
            UUID nationId,
            @NotNull TipoContaUsuario accountType,
            @Size(max = 128) String initialPassword) {}
    public record RespostaUsuario(UUID id, String email, PapelUsuario role, UUID tribeId, UUID nationId) {
        static RespostaUsuario de(UsuarioAcesso usuario) {
            return new RespostaUsuario(usuario.id(), usuario.email(), usuario.papel(),
                    usuario.triboId(), usuario.nacaoId());
        }
    }
    public record RespostaProvisionamento(UUID id, String email, PapelUsuario role, UUID tribeId,
            UUID nationId, TipoContaUsuario accountType) {
        static RespostaProvisionamento de(UsuarioAcesso usuario) {
            return new RespostaProvisionamento(usuario.id(), usuario.email(), usuario.papel(),
                    usuario.triboId(), usuario.nacaoId(), usuario.tipoConta());
        }
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public RespostaProvisionamento criar(@Valid @RequestBody RequisicaoCriacao corpo,
            HttpServletRequest requisicao) {
        AcessoService.ContextoAcesso ator = acesso.contexto(requisicao);
        if (!ator.assistente() && !ator.administradorSistema()) acesso.exigirAssistente(requisicao);
        UsuarioAcesso criado = provisionamento.criar(corpo.email(), corpo.role(), corpo.tribeId(),
                corpo.nationId(), corpo.accountType(), corpo.initialPassword(), ator.administradorSistema());
        return RespostaProvisionamento.de(criado);
    }

    @PutMapping("/{email}/role")
    public RespostaUsuario atribuirPapel(@PathVariable String email,
            @Valid @RequestBody RequisicaoPapel corpo, HttpServletRequest requisicao) {
        acesso.exigirAdministradorSistema(requisicao);
        return RespostaUsuario.de(usuarios.atribuirPapel(email, corpo.role(), corpo.tribeId(), corpo.nationId()));
    }
}
