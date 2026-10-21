package br.org.fju.rally.servico;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class ProvisionamentoUsuarioService {
    private final UsuarioAcessoService usuarios;
    private final ContaAcessoService contas;

    public ProvisionamentoUsuarioService(UsuarioAcessoService usuarios, ContaAcessoService contas) {
        this.usuarios = usuarios;
        this.contas = contas;
    }

    public record ResultadoCriacao(UsuarioAcesso usuario, String senhaProvisoria) {}

    @Transactional
    public ResultadoCriacao criar(String email, PapelUsuario papel, UUID triboId, UUID nacaoId,
            TipoContaUsuario tipoConta, String senhaInicial, boolean gerarSenhaProvisoria,
            boolean administrador) {
        if (papel == PapelUsuario.ADMINISTRADOR_SISTEMA && !administrador) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente um administrador do sistema pode criar outra conta de administrador.");
        }
        if (tipoConta == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o tipo de conta.");
        if (gerarSenhaProvisoria && tipoConta != TipoContaUsuario.LOCAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Senha provisória pode ser gerada apenas para conta local.");
        }

        String senha = senhaInicial == null ? null : senhaInicial;
        if (tipoConta == TipoContaUsuario.LOCAL) {
            if (gerarSenhaProvisoria) {
                if (senha != null && !senha.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Não envie initial_password junto com gerar_senha_provisoria.");
                }
            } else if (senha == null || senha.isBlank() || senha.length() < 12) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A senha inicial deve ter pelo menos 12 caracteres.");
            }
            if (!gerarSenhaProvisoria && senha.getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A senha inicial deve ter no máximo 72 bytes em UTF-8.");
            }
        } else if (senha != null && !senha.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Conta Google não deve receber uma senha local.");
        }

        UsuarioAcesso usuario = usuarios.criarProvisionado(email, papel, triboId, nacaoId, tipoConta);
        String senhaProvisoria = null;
        if (tipoConta == TipoContaUsuario.LOCAL) {
            if (gerarSenhaProvisoria) senhaProvisoria = contas.gerarSenhaProvisoria(usuario.id(), administrador);
            else contas.definirSenha(usuario.email(), senha);
        }
        return new ResultadoCriacao(usuario, senhaProvisoria);
    }
}
