package br.org.fju.rally.servico;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ContaAcessoService {
    private final JdbcTemplate banco;
    private final PasswordEncoder codificador;
    private final UsuarioAcessoService usuarios;
    private final GeradorSenhaProvisoria gerador;
    private final String hashInutil;

    public ContaAcessoService(JdbcTemplate banco, PasswordEncoder codificador, UsuarioAcessoService usuarios,
            GeradorSenhaProvisoria gerador) {
        this.banco = banco;
        this.codificador = codificador;
        this.usuarios = usuarios;
        this.gerador = gerador;
        this.hashInutil = codificador.encode("senha-inutil-para-tempo-de-resposta");
    }

    public enum EstadoLogin { AUTENTICADO, FALHA, BLOQUEADO }

    public record ResultadoLogin(EstadoLogin estado, String email, boolean trocaSenhaObrigatoria,
            long versaoCredenciais) {}

    private record Conta(String hash, int falhas, boolean bloqueada, boolean trocaSenhaObrigatoria,
            long versaoCredenciais) {}

    @Transactional
    public ResultadoLogin verificar(String emailInformado, String senha) {
        String email = normalizar(emailInformado);
        List<Conta> encontradas = banco.query(
                """
                SELECT c.senha_hash, c.tentativas_falhas, c.bloqueada_em IS NOT NULL,
                    c.troca_senha_obrigatoria, u.versao_credenciais
                FROM contas_acesso c JOIN usuarios_acesso u ON u.email = c.email
                WHERE c.email = ? FOR UPDATE OF c
                """,
                (rs, linha) -> new Conta(rs.getString(1), rs.getInt(2), rs.getBoolean(3),
                        rs.getBoolean(4), rs.getLong(5)), email);

        if (encontradas.isEmpty()) {
            codificador.matches(senha, hashInutil);
            return new ResultadoLogin(EstadoLogin.FALHA, null, false, 0L);
        }

        Conta conta = encontradas.getFirst();
        if (conta.bloqueada()) {
            return new ResultadoLogin(EstadoLogin.BLOQUEADO, email,
                    conta.trocaSenhaObrigatoria(), conta.versaoCredenciais());
        }

        if (!codificador.matches(senha, conta.hash())) {
            int falhas = Math.min(3, conta.falhas() + 1);
            banco.update("UPDATE contas_acesso SET tentativas_falhas = ?, bloqueada_em = CASE WHEN ? >= 3 THEN CURRENT_TIMESTAMP ELSE NULL END, atualizada_em = CURRENT_TIMESTAMP WHERE email = ?",
                    falhas, falhas, email);
            return new ResultadoLogin(falhas >= 3 ? EstadoLogin.BLOQUEADO : EstadoLogin.FALHA,
                    email, false, conta.versaoCredenciais());
        }

        banco.update("UPDATE contas_acesso SET tentativas_falhas = 0, atualizada_em = CURRENT_TIMESTAMP WHERE email = ?", email);
        return new ResultadoLogin(EstadoLogin.AUTENTICADO, email,
                conta.trocaSenhaObrigatoria(), conta.versaoCredenciais());
    }

    @Transactional
    public void definirSenha(String emailInformado, String senha) {
        String email = normalizar(emailInformado);
        if (usuarios.buscarPorEmail(email) == null) {
            throw new IllegalArgumentException("Esta conta não está autorizada a acessar o painel.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter no máximo 72 bytes em UTF-8.");
        }

        banco.update("""
                INSERT INTO contas_acesso(email, senha_hash, tentativas_falhas, bloqueada_em)
                VALUES (?, ?, 0, NULL)
                ON CONFLICT (email) DO UPDATE SET senha_hash = EXCLUDED.senha_hash,
                    tentativas_falhas = 0, bloqueada_em = NULL, troca_senha_obrigatoria = FALSE,
                    atualizada_em = CURRENT_TIMESTAMP
                """, email, codificador.encode(senha));
        banco.update("UPDATE usuarios_acesso SET versao_credenciais = versao_credenciais + 1 WHERE email = ?",
                email);
    }

    @Transactional
    public String gerarSenhaProvisoria(UUID usuarioId, boolean administrador) {
        UsuarioAcesso usuario = bloquearContaLocal(usuarioId);
        if (usuario.papel() == PapelUsuario.ADMINISTRADOR_SISTEMA && !administrador) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Somente administradores do sistema podem redefinir a senha de outra conta administradora.");
        }
        String senha = gerador.gerar();
        banco.update("""
                INSERT INTO contas_acesso(email, senha_hash, tentativas_falhas, bloqueada_em,
                    troca_senha_obrigatoria)
                VALUES (?, ?, 0, NULL, TRUE)
                ON CONFLICT (email) DO UPDATE SET senha_hash = EXCLUDED.senha_hash,
                    tentativas_falhas = 0, bloqueada_em = NULL, troca_senha_obrigatoria = TRUE,
                    atualizada_em = CURRENT_TIMESTAMP
                """, usuario.email(), codificador.encode(senha));
        banco.update("UPDATE usuarios_acesso SET versao_credenciais = versao_credenciais + 1 WHERE id = ?",
                usuarioId);
        return senha;
    }

    @Transactional
    public boolean exigeTrocaSenha(UUID usuarioId) {
        return Boolean.TRUE.equals(banco.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM usuarios_acesso u JOIN contas_acesso c ON c.email = u.email
                    WHERE u.id = ? AND c.troca_senha_obrigatoria = TRUE
                )
                """, Boolean.class, usuarioId));
    }

    @Transactional
    public void trocarSenhaProvisoria(UUID usuarioId, String senha) {
        if (senha == null || senha.length() < 12
                || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "A nova senha deve ter pelo menos 12 caracteres e no máximo 72 bytes em UTF-8.");
        }
        UsuarioAcesso usuario = bloquearContaLocal(usuarioId);
        int atualizadas = banco.update("""
                UPDATE contas_acesso SET senha_hash = ?, tentativas_falhas = 0, bloqueada_em = NULL,
                    troca_senha_obrigatoria = FALSE, atualizada_em = CURRENT_TIMESTAMP
                WHERE email = ? AND troca_senha_obrigatoria = TRUE
                """, codificador.encode(senha), usuario.email());
        if (atualizadas != 1) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "Não há troca de senha provisória pendente para esta conta.");
        }
        banco.update("UPDATE usuarios_acesso SET versao_credenciais = versao_credenciais + 1 WHERE id = ?",
                usuarioId);
    }

    private UsuarioAcesso bloquearContaLocal(UUID usuarioId) {
        var encontrados = banco.query("""
                SELECT id, email FROM usuarios_acesso WHERE id = ? AND tipo_conta = 'LOCAL' FOR UPDATE
                """, (rs, linha) -> new Object[] { rs.getObject(1, UUID.class), rs.getString(2) }, usuarioId);
        if (encontrados.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Conta local não encontrada.");
        }
        return usuarios.buscarPorId(usuarioId);
    }

    public static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public UsuarioAcesso usuario(String email) {
        return usuarios.buscarPorEmail(email);
    }

    public void registrarLogin(UUID usuarioId) {
        usuarios.registrarLogin(usuarioId);
    }

    public long versaoCredenciais(UUID usuarioId) {
        return usuarios.versaoCredenciais(usuarioId);
    }
}
