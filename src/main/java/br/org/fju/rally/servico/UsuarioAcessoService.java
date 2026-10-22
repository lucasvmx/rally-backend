package br.org.fju.rally.servico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UsuarioAcessoService {
    private final JdbcTemplate banco;

    @Value("${rally.autenticacao.assistentes:}")
    private String assistentes = "";

    @Value("${rally.autenticacao.administradores-sistema:}")
    private String administradoresSistema = "";

    public UsuarioAcessoService(JdbcTemplate banco) {
        this.banco = banco;
    }

    public UsuarioAcesso buscarPorEmail(String emailInformado) {
        if (emailInformado == null || emailInformado.isBlank()) return null;
        return buscar("email = ?", ContaAcessoService.normalizar(emailInformado));
    }

    public UsuarioAcesso buscarPorId(UUID id) {
        if (id == null) return null;
        return buscar("id = ?", id);
    }

    public UsuarioAcesso buscarPorGoogleSubject(String subject) {
        if (subject == null || subject.isBlank()) return null;
        return buscar("google_subject = ?", subject);
    }

    public record ContaGerenciada(UUID id, String email, PapelUsuario role, UUID tribeId, UUID nationId,
            TipoContaUsuario accountType, Instant criadaEm, Instant ultimoLoginEm) {}

    private static Instant instante(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime valor = rs.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    public List<ContaGerenciada> listarContas() {
        return banco.query("""
                SELECT id, email, papel, tribo_id, nacao_id, tipo_conta, criada_em, ultimo_login_em
                FROM usuarios_acesso ORDER BY email
                """, (rs, linha) -> new ContaGerenciada(
                rs.getObject("id", UUID.class), rs.getString("email"),
                PapelUsuario.valueOf(rs.getString("papel")), rs.getObject("tribo_id", UUID.class),
                rs.getObject("nacao_id", UUID.class), TipoContaUsuario.valueOf(rs.getString("tipo_conta")),
                instante(rs, "criada_em"), instante(rs, "ultimo_login_em")));
    }

    public ContaGerenciada buscarContaGerenciadaPorId(UUID id) {
        List<ContaGerenciada> contas = banco.query("""
                SELECT id, email, papel, tribo_id, nacao_id, tipo_conta, criada_em, ultimo_login_em
                FROM usuarios_acesso WHERE id = ?
                """, (rs, linha) -> new ContaGerenciada(
                rs.getObject("id", UUID.class), rs.getString("email"),
                PapelUsuario.valueOf(rs.getString("papel")), rs.getObject("tribo_id", UUID.class),
                rs.getObject("nacao_id", UUID.class), TipoContaUsuario.valueOf(rs.getString("tipo_conta")),
                instante(rs, "criada_em"), instante(rs, "ultimo_login_em")), id);
        return contas.isEmpty() ? null : contas.getFirst();
    }

    public long versaoCredenciais(UUID usuarioId) {
        List<Long> versoes = banco.query("SELECT versao_credenciais FROM usuarios_acesso WHERE id = ?",
                (rs, linha) -> rs.getLong(1), usuarioId);
        return versoes.isEmpty() ? -1L : versoes.getFirst();
    }

    public void registrarLogin(UUID usuarioId) {
        banco.update("UPDATE usuarios_acesso SET ultimo_login_em = CURRENT_TIMESTAMP WHERE id = ?", usuarioId);
    }

    public void registrarLoginGoogle(String googleSubject) {
        banco.update("UPDATE usuarios_acesso SET ultimo_login_em = CURRENT_TIMESTAMP WHERE google_subject = ?",
                googleSubject);
    }

    @Transactional
    public UsuarioAcesso autenticarGoogle(String emailInformado, boolean emailVerificado, String googleSubject) {
        if (!emailVerificado || emailInformado == null || emailInformado.isBlank()
                || googleSubject == null || googleSubject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O acesso pelo Google exige uma conta provisionada e um e-mail verificado.");
        }

        String email = ContaAcessoService.normalizar(emailInformado);
        if (identidadeGoogleRevogada(googleSubject)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta identidade Google não possui mais acesso ao Rally.");
        }
        UsuarioAcesso usuarioGoogle = buscarPorGoogleSubject(googleSubject);
        if (usuarioGoogle != null) {
            if (usuarioGoogle.tipoConta() == TipoContaUsuario.LOCAL) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Esta conta foi configurada para acesso com e-mail e senha.");
            }
            return usuarioGoogle;
        }

        UsuarioAcesso usuarioEmail = buscarPorEmail(email);
        if (usuarioEmail == null && emailRevogado(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta conta foi excluída e não pode ser provisionada automaticamente.");
        }
        if (usuarioEmail != null) {
            if (usuarioEmail.tipoConta() == TipoContaUsuario.LOCAL) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Esta conta foi configurada para acesso com e-mail e senha.");
            }
            if (usuarioEmail.googleSubject() != null && !usuarioEmail.googleSubject().equals(googleSubject)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Esta conta já está vinculada a outra identidade Google.");
            }
            if (isAdministradorInicial(email) && usuarioEmail.papel() != PapelUsuario.ADMINISTRADOR_SISTEMA) {
                banco.update("UPDATE usuarios_acesso SET papel = ?, tribo_id = NULL, nacao_id = NULL, atualizada_em = CURRENT_TIMESTAMP WHERE id = ?",
                        PapelUsuario.ADMINISTRADOR_SISTEMA.name(), usuarioEmail.id());
            }
            if (usuarioEmail.googleSubject() == null) {
                banco.update("UPDATE usuarios_acesso SET google_subject = ?, atualizada_em = CURRENT_TIMESTAMP WHERE id = ?",
                        googleSubject, usuarioEmail.id());
            }
            return buscarPorEmail(email);
        }

        PapelUsuario papelInicial;
        if (isAdministradorInicial(email)) {
            papelInicial = PapelUsuario.ADMINISTRADOR_SISTEMA;
        } else if (assistenteAutorizada(email)) {
            papelInicial = PapelUsuario.ASSISTENTE;
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta conta Google ainda não possui um papel autorizado no Rally.");
        }

        UUID id = UUID.randomUUID();
        banco.update("INSERT INTO usuarios_acesso(id, email, google_subject, papel, tipo_conta) VALUES (?, ?, ?, ?, 'GOOGLE') ON CONFLICT (email) DO NOTHING",
                id, email, googleSubject, papelInicial.name());
        UsuarioAcesso usuarioCriado = buscarPorEmail(email);
        if (usuarioCriado == null || !googleSubject.equals(usuarioCriado.googleSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Não foi possível vincular esta identidade Google ao usuário provisionado.");
        }
        return usuarioCriado;
    }

    @Transactional
    public UsuarioAcesso atribuirPapel(String emailInformado, PapelUsuario papel, UUID triboId, UUID nacaoId) {
        bloquearAdministradores();
        String email = ContaAcessoService.normalizar(emailInformado);
        validarEscopo(papel, triboId, nacaoId);
        if (isAdministradorInicial(email) && papel != PapelUsuario.ADMINISTRADOR_SISTEMA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O administrador inicial configurado não pode perder o papel de administrador do sistema.");
        }

        UsuarioAcesso atual = buscarPorEmailParaAtualizar(email);
        if (atual == null) {
            banco.update("INSERT INTO usuarios_acesso(id, email, papel, tribo_id, nacao_id) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), email, papel.name(), triboId, nacaoId);
        } else {
            if (atual.papel() == PapelUsuario.ADMINISTRADOR_SISTEMA
                    && papel != PapelUsuario.ADMINISTRADOR_SISTEMA) {
                exigirOutroAdministrador(atual.id());
            }
            banco.update("UPDATE usuarios_acesso SET papel = ?, tribo_id = ?, nacao_id = ?, atualizada_em = CURRENT_TIMESTAMP WHERE id = ?",
                    papel.name(), triboId, nacaoId, atual.id());
        }
        return buscarPorEmail(email);
    }

    @Transactional
    public UsuarioAcesso atualizar(UUID id, PapelUsuario papel, UUID triboId, UUID nacaoId,
            boolean administrador) {
        bloquearAdministradores();
        validarEscopo(papel, triboId, nacaoId);
        UsuarioAcesso atual = buscarPorIdParaAtualizar(id);
        if (atual == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada.");
        validarGestaoDoAlvo(atual, papel, administrador);
        if (atual.papel() == PapelUsuario.ADMINISTRADOR_SISTEMA
                && papel != PapelUsuario.ADMINISTRADOR_SISTEMA) {
            exigirOutroAdministrador(atual.id());
        }
        banco.update("UPDATE usuarios_acesso SET papel = ?, tribo_id = ?, nacao_id = ?, atualizada_em = CURRENT_TIMESTAMP WHERE id = ?",
                papel.name(), triboId, nacaoId, id);
        return buscarPorId(id);
    }

    @Transactional
    public void excluir(UUID id, UUID atorId, boolean administrador) {
        bloquearAdministradores();
        UsuarioAcesso alvo = buscarPorIdParaAtualizar(id);
        if (alvo == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada.");
        if (id.equals(atorId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é permitido excluir a própria conta.");
        }
        validarGestaoDoAlvo(alvo, null, administrador);
        if (isAdministradorInicial(alvo.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O administrador inicial configurado não pode ser excluído.");
        }
        if (alvo.papel() == PapelUsuario.ADMINISTRADOR_SISTEMA) exigirOutroAdministrador(alvo.id());
        banco.update("""
                INSERT INTO usuarios_acesso_revogados(email, google_subject, revogada_em)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (email) DO UPDATE SET google_subject = COALESCE(EXCLUDED.google_subject,
                    usuarios_acesso_revogados.google_subject), revogada_em = CURRENT_TIMESTAMP
                """, alvo.email(), alvo.googleSubject());
        banco.update("DELETE FROM contas_acesso WHERE email = ?", alvo.email());
        banco.update("DELETE FROM usuarios_acesso WHERE id = ?", id);
    }

    private UsuarioAcesso buscarPorIdParaAtualizar(UUID id) {
        List<UsuarioAcesso> encontrados = banco.query("""
                SELECT id, email, papel, tribo_id, nacao_id, google_subject, tipo_conta
                FROM usuarios_acesso WHERE id = ? FOR UPDATE
                """, (rs, linha) -> mapearUsuario(rs), id);
        return encontrados.isEmpty() ? null : encontrados.getFirst();
    }

    private UsuarioAcesso buscarPorEmailParaAtualizar(String email) {
        List<UsuarioAcesso> encontrados = banco.query("""
                SELECT id, email, papel, tribo_id, nacao_id, google_subject, tipo_conta
                FROM usuarios_acesso WHERE email = ? FOR UPDATE
                """, (rs, linha) -> mapearUsuario(rs), email);
        return encontrados.isEmpty() ? null : encontrados.getFirst();
    }

    private void validarGestaoDoAlvo(UsuarioAcesso alvo, PapelUsuario novoPapel, boolean administrador) {
        if (!administrador && (alvo.papel() == PapelUsuario.ADMINISTRADOR_SISTEMA
                || novoPapel == PapelUsuario.ADMINISTRADOR_SISTEMA)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Somente administradores do sistema podem gerir contas de administrador.");
        }
        if (isAdministradorInicial(alvo.email()) && novoPapel != null
                && novoPapel != PapelUsuario.ADMINISTRADOR_SISTEMA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O administrador inicial configurado deve permanecer administrador do sistema.");
        }
    }

    private List<UUID> bloquearAdministradores() {
        List<UUID> administradores = banco.query("""
                SELECT id FROM usuarios_acesso WHERE papel = 'ADMINISTRADOR_SISTEMA'
                ORDER BY id FOR UPDATE
                """, (rs, linha) -> rs.getObject(1, UUID.class));
        return administradores;
    }

    private void exigirOutroAdministrador(UUID administradorAlvoId) {
        List<UUID> administradores = bloquearAdministradores();
        if (administradores.size() <= 1 && administradores.contains(administradorAlvoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O sistema precisa manter ao menos um administrador.");
        }
    }

    @Transactional
    public UsuarioAcesso criarProvisionado(String emailInformado, PapelUsuario papel, UUID triboId,
            UUID nacaoId, TipoContaUsuario tipoConta) {
        String email = ContaAcessoService.normalizar(emailInformado);
        validarEscopo(papel, triboId, nacaoId);
        if (tipoConta == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o tipo de conta.");
        if (isAdministradorInicial(email) && papel != PapelUsuario.ADMINISTRADOR_SISTEMA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O administrador inicial configurado deve permanecer administrador do sistema.");
        }
        if (buscarPorEmail(email) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta cadastrada com este e-mail.");
        }

        try {
            banco.update("INSERT INTO usuarios_acesso(id, email, papel, tribo_id, nacao_id, tipo_conta) VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), email, papel.name(), triboId, nacaoId, tipoConta.name());
        } catch (DuplicateKeyException excecao) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta cadastrada com este e-mail.");
        }
        return buscarPorEmail(email);
    }

    public boolean isAdministradorInicial(String email) {
        return contemEmail(administradoresSistema, email);
    }

    private void validarEscopo(PapelUsuario papel, UUID triboId, UUID nacaoId) {
        if (papel == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um papel válido.");
        if (papel == PapelUsuario.COORDENADOR_TRIBO) {
            if (triboId == null || nacaoId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "COORDENADOR_TRIBO exige tribe_id e não aceita nation_id.");
            }
            if (!existe("tribos", triboId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A tribo informada não existe.");
            }
        } else if (papel == PapelUsuario.COORDENADOR_NACAO) {
            if (nacaoId == null || triboId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "COORDENADOR_NACAO exige nation_id e não aceita tribe_id.");
            }
            if (!existe("nacoes", nacaoId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nação informada não existe.");
            }
        } else if (triboId != null || nacaoId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ASSISTENTE e ADMINISTRADOR_SISTEMA não aceitam escopo de tribo ou nação.");
        }
    }

    private boolean existe(String tabela, UUID id) {
        return Boolean.TRUE.equals(banco.queryForObject("SELECT EXISTS (SELECT 1 FROM " + tabela + " WHERE id = ?)",
                Boolean.class, id));
    }

    private boolean assistenteAutorizada(String email) {
        return contemEmail(assistentes, email);
    }

    private boolean identidadeGoogleRevogada(String googleSubject) {
        return Boolean.TRUE.equals(banco.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM usuarios_acesso_revogados WHERE google_subject = ?)
                """, Boolean.class, googleSubject));
    }

    private boolean emailRevogado(String email) {
        return Boolean.TRUE.equals(banco.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM usuarios_acesso_revogados WHERE email = ?)
                """, Boolean.class, email));
    }

    private static boolean contemEmail(String lista, String email) {
        if (email == null || email.isBlank()) return false;
        return Arrays.stream(lista.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> !item.isBlank() && item.equals(email.trim().toLowerCase(Locale.ROOT)));
    }

    private UsuarioAcesso buscar(String condicao, Object valor) {
        List<UsuarioAcesso> usuarios = banco.query("SELECT id, email, papel, tribo_id, nacao_id, google_subject, tipo_conta FROM usuarios_acesso WHERE " + condicao,
                (rs, linha) -> mapearUsuario(rs), valor);
        return usuarios.isEmpty() ? null : usuarios.getFirst();
    }

    private static UsuarioAcesso mapearUsuario(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UsuarioAcesso(rs.getObject("id", UUID.class), rs.getString("email"),
                PapelUsuario.valueOf(rs.getString("papel")), rs.getObject("tribo_id", UUID.class),
                rs.getObject("nacao_id", UUID.class), rs.getString("google_subject"),
                TipoContaUsuario.valueOf(rs.getString("tipo_conta")));
    }
}
