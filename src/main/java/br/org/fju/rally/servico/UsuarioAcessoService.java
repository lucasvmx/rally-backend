package br.org.fju.rally.servico;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    @Transactional
    public UsuarioAcesso autenticarGoogle(String emailInformado, boolean emailVerificado, String googleSubject) {
        if (!emailVerificado || emailInformado == null || emailInformado.isBlank()
                || googleSubject == null || googleSubject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "O acesso pelo Google exige uma conta provisionada e um e-mail verificado.");
        }

        String email = ContaAcessoService.normalizar(emailInformado);
        UsuarioAcesso usuarioGoogle = buscarPorGoogleSubject(googleSubject);
        if (usuarioGoogle != null) return usuarioGoogle;

        UsuarioAcesso usuarioEmail = buscarPorEmail(email);
        if (usuarioEmail != null) {
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
        banco.update("INSERT INTO usuarios_acesso(id, email, google_subject, papel) VALUES (?, ?, ?, ?) ON CONFLICT (email) DO NOTHING",
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
        String email = ContaAcessoService.normalizar(emailInformado);
        validarEscopo(papel, triboId, nacaoId);
        if (isAdministradorInicial(email) && papel != PapelUsuario.ADMINISTRADOR_SISTEMA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O administrador inicial configurado não pode perder o papel de administrador do sistema.");
        }

        UsuarioAcesso atual = buscarPorEmail(email);
        if (atual == null) {
            banco.update("INSERT INTO usuarios_acesso(id, email, papel, tribo_id, nacao_id) VALUES (?, ?, ?, ?, ?)",
                    UUID.randomUUID(), email, papel.name(), triboId, nacaoId);
        } else {
            banco.update("UPDATE usuarios_acesso SET papel = ?, tribo_id = ?, nacao_id = ?, atualizada_em = CURRENT_TIMESTAMP WHERE id = ?",
                    papel.name(), triboId, nacaoId, atual.id());
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

    private static boolean contemEmail(String lista, String email) {
        if (email == null || email.isBlank()) return false;
        return Arrays.stream(lista.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> !item.isBlank() && item.equals(email.trim().toLowerCase(Locale.ROOT)));
    }

    private UsuarioAcesso buscar(String condicao, Object valor) {
        List<UsuarioAcesso> usuarios = banco.query("SELECT id, email, papel, tribo_id, nacao_id, google_subject FROM usuarios_acesso WHERE " + condicao,
                (rs, linha) -> new UsuarioAcesso(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        PapelUsuario.valueOf(rs.getString("papel")),
                        rs.getObject("tribo_id", UUID.class),
                        rs.getObject("nacao_id", UUID.class),
                        rs.getString("google_subject")), valor);
        return usuarios.isEmpty() ? null : usuarios.getFirst();
    }
}
