package br.org.fju.rally.api;

import br.org.fju.rally.servico.AcessoService;
import br.org.fju.rally.servico.PapelUsuario;
import br.org.fju.rally.servico.UsuarioAcesso;
import br.org.fju.rally.servico.UsuarioAcessoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    public UsuariosController(AcessoService acesso, UsuarioAcessoService usuarios) {
        this.acesso = acesso;
        this.usuarios = usuarios;
    }

    public record RequisicaoPapel(@NotNull PapelUsuario role, UUID tribeId, UUID nationId) {}
    public record RespostaUsuario(UUID id, String email, PapelUsuario role, UUID tribeId, UUID nationId) {
        static RespostaUsuario de(UsuarioAcesso usuario) {
            return new RespostaUsuario(usuario.id(), usuario.email(), usuario.papel(),
                    usuario.triboId(), usuario.nacaoId());
        }
    }

    @PutMapping("/{email}/role")
    public RespostaUsuario atribuirPapel(@PathVariable String email,
            @Valid @RequestBody RequisicaoPapel corpo, HttpServletRequest requisicao) {
        acesso.exigirAdministradorSistema(requisicao);
        return RespostaUsuario.de(usuarios.atribuirPapel(email, corpo.role(), corpo.tribeId(), corpo.nationId()));
    }
}
