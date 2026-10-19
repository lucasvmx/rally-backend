package br.org.fju.rally.servico;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class ContaAcessoService {
    private final JdbcTemplate banco;
    private final PasswordEncoder codificador;
    private final AcessoService acesso;
    private final String hashInutil;

    public ContaAcessoService(JdbcTemplate banco, PasswordEncoder codificador, AcessoService acesso) {
        this.banco = banco;
        this.codificador = codificador;
        this.acesso = acesso;
        this.hashInutil = codificador.encode("senha-inutil-para-tempo-de-resposta");
    }

    public enum EstadoLogin { AUTENTICADO, FALHA, BLOQUEADO }

    public record ResultadoLogin(EstadoLogin estado, String email) {}

    private record Conta(String hash, int falhas, boolean bloqueada) {}

    @Transactional
    public ResultadoLogin verificar(String emailInformado, String senha) {
        String email = normalizar(emailInformado);
        List<Conta> encontradas = banco.query(
                "SELECT senha_hash, tentativas_falhas, bloqueada_em IS NOT NULL FROM contas_acesso WHERE email = ? FOR UPDATE",
                (rs, linha) -> new Conta(rs.getString(1), rs.getInt(2), rs.getBoolean(3)), email);

        if (encontradas.isEmpty() || !acesso.assistenteAutorizada(email)) {
            codificador.matches(senha, hashInutil);
            return new ResultadoLogin(EstadoLogin.FALHA, null);
        }

        Conta conta = encontradas.getFirst();
        if (conta.bloqueada()) {
            return new ResultadoLogin(EstadoLogin.BLOQUEADO, email);
        }

        if (!codificador.matches(senha, conta.hash())) {
            int falhas = Math.min(3, conta.falhas() + 1);
            banco.update("UPDATE contas_acesso SET tentativas_falhas = ?, bloqueada_em = CASE WHEN ? >= 3 THEN CURRENT_TIMESTAMP ELSE NULL END, atualizada_em = CURRENT_TIMESTAMP WHERE email = ?",
                    falhas, falhas, email);
            return new ResultadoLogin(falhas >= 3 ? EstadoLogin.BLOQUEADO : EstadoLogin.FALHA, email);
        }

        banco.update("UPDATE contas_acesso SET tentativas_falhas = 0, atualizada_em = CURRENT_TIMESTAMP WHERE email = ?", email);
        return new ResultadoLogin(EstadoLogin.AUTENTICADO, email);
    }

    @Transactional
    public void definirSenha(String emailInformado, String senha) {
        String email = normalizar(emailInformado);
        if (!acesso.assistenteAutorizada(email)) {
            throw new IllegalArgumentException("Esta conta não está autorizada a acessar o painel.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter no máximo 72 bytes em UTF-8.");
        }

        banco.update("""
                INSERT INTO contas_acesso(email, senha_hash, tentativas_falhas, bloqueada_em)
                VALUES (?, ?, 0, NULL)
                ON CONFLICT (email) DO UPDATE SET senha_hash = EXCLUDED.senha_hash,
                    tentativas_falhas = 0, bloqueada_em = NULL, atualizada_em = CURRENT_TIMESTAMP
                """, email, codificador.encode(senha));
    }

    public static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
