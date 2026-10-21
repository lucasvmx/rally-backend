package br.org.fju.rally.config;

import br.org.fju.rally.servico.ContaAcessoService;
import br.org.fju.rally.servico.UsuarioAcesso;
import br.org.fju.rally.servico.UsuarioLocalPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AutenticacaoLocalProvider implements AuthenticationProvider {
    private final ContaAcessoService contas;

    public AutenticacaoLocalProvider(ContaAcessoService contas) {
        this.contas = contas;
    }

    @Override
    public Authentication authenticate(Authentication autenticacao) {
        var resultado = contas.verificar(autenticacao.getName(), String.valueOf(autenticacao.getCredentials()));
        if (resultado.estado() == ContaAcessoService.EstadoLogin.BLOQUEADO) {
            throw new LockedException("A conta foi bloqueada após três tentativas de login inválidas.");
        }
        if (resultado.estado() != ContaAcessoService.EstadoLogin.AUTENTICADO) {
            throw new BadCredentialsException("E-mail ou senha incorretos.");
        }

        UsuarioAcesso usuario = contas.usuario(resultado.email());
        if (usuario == null) {
            throw new BadCredentialsException("E-mail ou senha incorretos.");
        }
        contas.registrarLogin(usuario.id());
        UsuarioLocalPrincipal principal = new UsuarioLocalPrincipal(usuario,
                resultado.trocaSenhaObrigatoria(), resultado.versaoCredenciais());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> tipoAutenticacao) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipoAutenticacao);
    }
}
