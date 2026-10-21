package br.org.fju.rally.servico;

import java.util.UUID;

public record UsuarioAcesso(UUID id, String email, PapelUsuario papel, UUID triboId, UUID nacaoId,
                            String googleSubject, TipoContaUsuario tipoConta) {
    public UsuarioAcesso(UUID id, String email, PapelUsuario papel, UUID triboId, UUID nacaoId,
                         String googleSubject) {
        this(id, email, papel, triboId, nacaoId, googleSubject, TipoContaUsuario.GOOGLE);
    }
}
